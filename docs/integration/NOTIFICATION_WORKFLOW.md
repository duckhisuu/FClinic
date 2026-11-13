# Notification Workflow — Patient Books an Appointment

This document traces the complete path from a patient clicking **Book** in the React app to an email appearing in MailHog. It covers the outbox pattern, every service-to-service call, the exact methods invoked, and the roles of RabbitMQ and MailHog.

---

## 1. High-level picture

```
┌─────────────┐        POST /api/v2/appointments           ┌─────────────────────┐
│  React FE   │ ──────────────────────────────────────────▶ │  appointment-service │
│ (patient)   │        Idempotency-Key: <uuid>              │      :8083           │
└─────────────┘                                             └──────────┬──────────┘
                                                                       │ 1. Feign: patient-service  (profile + email)
                                                                       │ 2. Feign: doctor-service   (doctor + slot)
                                                                       │ 3. Feign: auth-service     (recipient accounts)
                                                                       │ 4. TX: appointments + history + outbox_events
                                                                       ▼
                                                            ┌─────────────────────┐
                                                            │ PostgreSQL           │
                                                            │ appointment_db       │
                                                            │  outbox_events       │
                                                            └──────────┬──────────┘
                                                                       │ 5. poll every 5 s
                                                                       ▼
                                                            ┌─────────────────────┐
                                                            │  outbox relay job    │
                                                            │ (appointment-service)│
                                                            └──────────┬──────────┘
                                                                       │ 6. publish to
                                                                       │    exchange clinic.events
                                                                       ▼
                                                            ┌─────────────────────┐
                                                            │      RabbitMQ        │
                                                            │  topic: clinic.events│
                                                            │  q: notification.    │
                                                            │     appointment.q    │
                                                            └──────────┬──────────┘
                                                                       │ 7. @RabbitListener
                                                                       ▼
                                                            ┌─────────────────────┐
                                                            │ notification-service │
                                                            │ :8084                │
                                                            │  ingest → DB         │
                                                            │  scheduler → SMTP    │
                                                            └──────────┬──────────┘
                                                                       │ 8. SMTP :1025
                                                                       ▼
                                                            ┌─────────────────────┐
                                                            │       MailHog        │
                                                            │  UI: :8025           │
                                                            └─────────────────────┘
```

Two important design rules:

- **Phase A (ingest) never sends email.** The RabbitMQ consumer only writes durable `notifications` rows.
- **Phase B (delivery) is a scheduled worker.** A separate scheduler claims rows and sends SMTP, so a slow/unavailable mail server never blocks the broker listener.

---

## 2. Step by step

### Step 0 — Frontend trigger

`ClinicAppointmentFE/src/appointment/api.js:86`

```js
book: (body, key) => mutation("post", "/api/v2/appointments", body, key),
```

`mutation()` (`api.js:75`) attaches `Idempotency-Key: <uuid>` to every POST/PUT. The request goes through the Vite proxy (or the Gateway) to `appointment-service:8083`.

### Step 1 — Controller

`services/appointment-service/.../api/controller/AppointmentController.java:52`

```java
@PostMapping
public ResponseEntity<String> bookAppointment(
    @RequestAttribute("appointmentActor") AppointmentActor actor,
    @RequestHeader("Idempotency-Key") String key,
    @Valid @RequestBody BookAppointmentRequest body)
```

Before this method runs, the security filter chain has already:

1. Introspected the bearer against **auth-service** (`POST /api/v1/auth/introspect`).
2. Checked the appointment work-scope against **auth-service** (`POST /api/v1/auth/scopes`).
3. Built an `AppointmentActor` request attribute.

### Step 2 — Application service / use case

`services/appointment-service/.../application/usecase/AppointmentApplicationService.java:45`

```java
public AppointmentOperationResult bookAppointment(BookAppointmentCommand command)
```

It does three things:

1. `policy.mutate(...)` — authorization check for `APPOINTMENT_CREATE`.
2. Builds a normalized request body (`patientId`, `doctorId`, `slotId`, `reasonForVisit`).
3. Calls `IdempotentAppointmentOperation.execute(...)` with operation `CREATE`.

`IdempotentAppointmentOperation` (`.../usecase/IdempotentAppointmentOperation.java`) takes a distributed lock (`PostgresDistributedLockAdapter` by default, `APPOINTMENT_LOCK_PROVIDER=postgres`) and looks in `operation_results`. If the same actor + operation + idempotency key already exists with the same request hash, it replays the stored response instead of booking twice.

### Step 3 — Remote references (all before the DB transaction)

Inside the `work` callback (`AppointmentApplicationService.java:64-70`):

```java
var patient = patients.patient(patientId, deadline);   // Feign → patient-service
var doctor  = doctors.doctor(doctorId, deadline);       // Feign → doctor-service
var slot    = doctors.slot(doctor.id(), slotId, ...);   // Feign → doctor-service
slot.requireBookable(doctor.id(), clock);
```

Implementations are in `FeignAppointmentReferenceAdapter`:

- `patient(...)` → `PatientClient.getPatient(...)` → `GET /api/v3/patients/{id}`.
  Requires status `ACTIVE`, a valid `patientCode` and an E.164 phone.
- `doctor(...)` → `DoctorClient.getDoctor(...)` → `GET /api/v1/doctors/{id}`.
  Requires `active = true`.
- `slot(...)` → `DoctorClient.getSlot(...)` → doctor-service slot endpoint.
  `slot.requireBookable()` rejects past/unpublished slots.

Doing the remote calls **before** opening the local transaction avoids holding a DB transaction across HTTP.

### Step 4 — Local write transaction and outbox insert

`transactions.execute(...)` (Spring `TransactionTemplate`, propagation `REQUIRED`) runs:

```java
var current = Appointment.createNew(...);          // domain aggregate
var saved = repository.save(current);              // INSERT appointments
history.record(saved, null, "CREATED", ...);       // INSERT appointment_history
events.publish(new AppointmentBookedDomainEvent(   // INSERT outbox_events
    UUID.randomUUID(), now, AppointmentEventSnapshot.from(saved)));
return operations.complete(attempt, AppointmentView.from(saved), 201, now);
```

`events` is `OutboxAppointmentEventPublisher` (`.../infrastructure/adapter/OutboxAppointmentEventPublisher.java`). Its `publish()` is annotated:

```java
@Transactional(propagation = Propagation.MANDATORY)
```

So the outbox row **must** commit atomically with the appointment. If the transaction rolls back, no event row exists; if the event row exists, the appointment exists. This is the transactional outbox guarantee.

The row written to `outbox_events`:

| Column | Content |
|---|---|
| `id` | event UUID |
| `aggregate_id` | appointment UUID |
| `aggregate_version` | appointment version |
| `event_type` | `APPOINTMENT_CREATED` |
| `intent` | JSON snapshot: ids, names, phone, slot, date/time, `schemaVersion: 3`, `source: appointment-service` — **no reason/diagnosis fields** (enforced by a DB check constraint) |
| `payload` | `NULL` (enriched later) |
| `status` | `PENDING_ENRICHMENT` |

Table definition: `services/appointment-service/src/main/resources/db/migration/V1__appointment_v2.sql:31-40`.

### Step 5 — Outbox relay job

`OutboxRelayJob` (`.../infrastructure/adapter/OutboxRelayJob.java`) runs:

```java
@Scheduled(fixedDelayString = "${appointment.outbox-poll-ms:5000}")
public void relay() { relay.publishPendingEvents(); }
```

So every **5 seconds** `PublishOutboxService.publishPendingEvents()` runs.

### Step 6 — Claim → enrich → publish

`PublishOutboxService` (`.../application/usecase/PublishOutboxService.java:35`):

```java
public void publishPendingEvents() {
  if (!policy.outboxEnabled()) return;                 // APPOINTMENT_OUTBOX_ENABLED
  for (var row : outbox.claim(clock.instant())) {
    ...
    String payload = row.payload();
    if (payload == null) {
      payload = payloads.resolvePayload(row);          // Feign → auth-service (recipients)
      outbox.ready(row, payload, clock.instant());
    }
    broker.publish(row, payload);                      // RabbitMQ
    outbox.published(row, clock.instant());
  }
}
```

**6a. Claim.** `JdbcOutboxStoreAdapter.claim()` uses `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1` with a 30 s `claimed_until` lease, so multiple service instances never publish the same row.

**6b. Enrich.** `NotificationPayloadAdapter.resolvePayload()`:

```java
var resolved = recipients.recipients(patientId, doctorId);
((ObjectNode) n).set("recipients", JSON.valueToTree(resolved));
```

`RecipientLookupPort.recipients()` is implemented by `FeignAppointmentReferenceAdapter.recipients()` (`.../infrastructure/adapter/FeignAppointmentReferenceAdapter.java:84`):

1. Re-reads the patient profile from **patient-service** (`GET /api/v3/patients/{id}`) to obtain `fullName` and `email`.
2. Calls **auth-service** `POST /api/v1/auth/internal/appointment-recipient-accounts` via `AuthRecipientClient`, authenticated with the dedicated service credentials (`app.recipients.client-id/secret`).
3. Returns exactly two recipients:
   - `PATIENT` — `userId` may be `null` for a walk-in patient with no account; name/email come from the patient profile.
   - `DOCTOR` — `userId`, `name`, `email` resolved from the doctor's auth account.

Auth endpoint: `services/auth-service/.../api/controller/AppointmentRecipientController.java`.
Business logic: `AppointmentRecipientService.resolve()` — read-only, never joins by phone/email, validates the doctor account is active and assigned to that `doctorId`, and returns `503` if no unique mapping exists.

The enriched payload is stored back as `READY` with `payload` filled in.

**6c. Publish.** `RabbitNotificationPublisherAdapter.publish()` (`.../infrastructure/adapter/RabbitNotificationPublisherAdapter.java`):

| Event type | Routing key |
|---|---|
| `APPOINTMENT_CREATED` | `appointment.created` |
| `APPOINTMENT_CANCELLED` | `appointment.cancelled` |
| `APPOINTMENT_RESCHEDULED` | `appointment.rescheduled` |
| `APPOINTMENT_REMINDER` | `appointment.reminder` |

It sends a **persistent JSON** message to exchange `clinic.events`, sets `messageId = outbox row id`, and waits up to 5 s for the broker publisher-confirm. If the broker does not ack (or returns the message), it throws, and `PublishOutboxService` calls `outbox.retry(...)` with a 30 s backoff (`BROKER_UNAVAILABLE` or `DEPENDENCY_UNAVAILABLE`).

**6d. Mark published.** `outbox_events.status = 'PUBLISHED'`, `published_at` set.

### Step 7 — RabbitMQ

Declarations exist in both services and converge on the same objects:

- Exchange: `clinic.events` (topic, durable).
- Queue consumed by notification: `notification.appointment.q` (durable) — `services/notification-service/.../config/RabbitMqConfig.java`.
- Bindings: the four routing keys above.
- Dead-letter: `clinic.dlx` → `notification.appointment.dlq`.

`notification-service/application.yml` listener settings:

```yaml
listener:
  simple:
    acknowledge-mode: auto
    default-requeue-rejected: false   # rejected messages go to the DLX, not back to the queue
    prefetch: 20
    retry:
      enabled: true
      max-attempts: 3
      initial-interval: 1s
      multiplier: 2
```

> Note: `appointment-service` also declares a queue named `fclinic.appointment.notifications` bound to the same exchange with the same keys (`appointment-service/.../config/RabbitMQConfig.java`). No code consumes it. The real consumer queue is `notification.appointment.q`, declared by notification-service. The extra declaration is a legacy leftover; messages delivered to it will accumulate. There is a known mismatch here worth cleaning up.

### Step 8 — Consumer: ingest only (Phase A)

`AppointmentEventListener` (`.../infrastructure/messaging/AppointmentEventListener.java`):

```java
@RabbitListener(queues = RabbitMqConfig.NOTIFICATION_QUEUE)
public void consume(AppointmentNotificationEvent event) {
    ingestUseCase.ingest(event);
}
```

Note the class comment: *"Thin by design: failures propagate so listener retry and the DLX handle them. No SMTP here."*

`NotificationIngestionService.ingest()` (`.../application/usecase/NotificationIngestionService.java`):

1. **Validate** the event:
   - `schemaVersion == 3`, `source == "appointment-service"`.
   - Exactly one `PATIENT` and one `DOCTOR` recipient (if `recipients` present), no mixed flattened/nested representations.
   - Bean Validation on the event, patient, doctor, appointment.
   - UUIDv4 for appointment/doctor (and patient if present).
   - Reschedule events must carry `previousSlot`.
2. In **one transaction**:
   - Check `processed_events` by `eventId`; if present → increment `notification.events.duplicate` and stop (idempotent consumption).
   - `processedEventPort.save(event, now)` → `INSERT processed_events` (flushed immediately so a duplicate race fails here).
   - For each recipient with a non-blank email, `createNotification(...)`:
     - Map event type → notification type:
       | Event | Notification type |
       |---|---|
       | `APPOINTMENT_CREATED` | `APPOINTMENT_CONFIRMATION` |
       | `APPOINTMENT_CANCELLED` | `APPOINTMENT_CANCELLATION` |
       | `APPOINTMENT_RESCHEDULED` | `APPOINTMENT_RESCHEDULED` |
       | `APPOINTMENT_REMINDER` | `APPOINTMENT_REMINDER` |
     - `templateRenderPort.render(type, recipientType, variables)`.
     - `notificationRepository.save(Notification.pending(...))` → `INSERT notifications` with `status = PENDING`.
   - Commit.

Template rendering: `ThymeleafTemplateAdapter` loads the active row from `notification_templates` (seeded by `V3__seed_notification_templates.sql`, one template per notification type × recipient type) and renders:
- subject with the TEXT engine,
- HTML body with the HTML engine (variables HTML-escaped).

Recipients without email (e.g., a patient with no account yet) are skipped with a log line, not treated as failures.

### Step 9 — Delivery scheduler (Phase B)

`NotificationDeliveryScheduler` (`.../infrastructure/scheduler/NotificationDeliveryScheduler.java`):

```java
@Scheduled(fixedDelayString = "${app.notification.delivery.scheduler-delay-ms:5000}")
public void deliverDueNotifications() { deliveryUseCase.processDueNotifications(); }
```

Every 5 s, `NotificationDeliveryService.processDueNotifications()`:

```java
for (UUID id : stateService.findDueIds()) { deliver(id); }
```

`NotificationStateService` performs transaction-scoped state transitions using the domain aggregate:

1. `findDueIds()` → rows in `PENDING` or `RETRY_SCHEDULED` whose `next_attempt_at <= now`, limited by `batch-size` (100).
2. `claim(id)` → atomic conditional update `PENDING/RETRY_SCHEDULED → PROCESSING`. If 0 rows changed, another worker owns it.
3. `getRequired(id)` → load the notification.
4. **No DB transaction is held during SMTP** — `SmtpEmailSender.sendHtml(...)` is called outside the transaction.
5. On success → `markSent(id)` → `SENT`, `sent_at`.
6. On failure → `recordFailure(id, code, message)`:
   - If `attemptCount + 1 >= max-attempts` (default 3) → `PERMANENT_FAILURE`.
   - Else → `RETRY_SCHEDULED` with delay from `retry-delays` (`1m`, `5m`).

A separate `StuckNotificationRecoveryScheduler` runs every 60 s and resets rows stuck in `PROCESSING` past `processing-timeout` (default 10 m), so a crash mid-send cannot strand a notification.

Status lifecycle:

```
PENDING ──claim──▶ PROCESSING ──sent──▶ SENT
   ▲                    │
   │                    └──fail (retry)──▶ RETRY_SCHEDULED ──claim──▶ PROCESSING
   │
   └──fail (max attempts)──▶ PERMANENT_FAILURE
```

### Step 10 — MailHog

`SmtpEmailSender` (`.../infrastructure/mail/SmtpEmailSender.java`) builds a MIME message:

```java
helper.setFrom(fromAddress, fromName);   // no-reply@fclinic.local / FClinic
helper.setTo(to.value());
helper.setSubject(subject);
helper.setText(htmlBody, true);          // HTML
mailSender.send(message);
```

The `JavaMailSender` uses `spring.mail.*` (`MAIL_HOST`, `MAIL_PORT`). Locally:

- SMTP is `localhost:1025`.
- MailHog container (`docker-compose.yml:219-227`) listens on `1025` and exposes a web UI on `8025`.
- MailHog accepts every message and displays it at **http://localhost:8025**.

MailHog is a **fake SMTP sink**: it does not deliver real mail. It exists so the flow can be verified end-to-end without an external provider. In production, `MAIL_HOST`/`MAIL_PORT`/credentials point at a real SMTP server instead.

---

## 3. Method call summary

| # | Caller | Method | Target | What it does |
|---|---|---|---|---|
| 1 | FE `api.js` | `mutation("post", …)` | `POST /api/v2/appointments` | Sends `Idempotency-Key` + body |
| 2 | `AppointmentController` | `bookAppointment(...)` | `AppointmentApplicationService` | Builds command |
| 3 | `AppointmentApplicationService` | `bookAppointment(...)` | `IdempotentAppointmentOperation.execute(...)` | Lock + idempotency check |
| 4 | `FeignAppointmentReferenceAdapter` | `patient(...)` | patient-service `GET /api/v3/patients/{id}` | Name/phone/email/status |
| 5 | `FeignAppointmentReferenceAdapter` | `doctor(...)`, `slot(...)` | doctor-service | Doctor + slot validity |
| 6 | `AppointmentApplicationService` | `repository.save(...)` | `appointment_db.appointments` | Persist appointment |
| 7 | `AppointmentApplicationService` | `history.record(...)` | `appointment_history` | Audit row |
| 8 | `OutboxAppointmentEventPublisher` | `publish(event)` | `outbox_events` | Atomic outbox row (`PENDING_ENRICHMENT`) |
| 9 | `OutboxRelayJob` | `relay()` every 5 s | `PublishOutboxService` | Poll |
| 10 | `PublishOutboxService` | `outbox.claim(now)` | `JdbcOutboxStoreAdapter` | `FOR UPDATE SKIP LOCKED`, 30 s lease |
| 11 | `NotificationPayloadAdapter` | `resolvePayload(row)` | `RecipientLookupPort` | Add recipients |
| 12 | `FeignAppointmentReferenceAdapter` | `recipients(...)` | patient-service + auth-service | Email addresses |
| 13 | `PublishOutboxService` | `outbox.ready(...)` | `outbox_events` | Store enriched `payload`, `READY` |
| 14 | `RabbitNotificationPublisherAdapter` | `publish(row, payload)` | RabbitMQ `clinic.events` | Persistent message + confirm |
| 15 | `PublishOutboxService` | `outbox.published(...)` | `outbox_events` | `PUBLISHED` |
| 16 | `AppointmentEventListener` | `consume(event)` | `NotificationIngestionService.ingest(...)` | Phase A |
| 17 | `NotificationIngestionService` | `processedEventPort.save(...)` | `processed_events` | Dedup marker |
| 18 | `NotificationIngestionService` | `templateRenderPort.render(...)` | `ThymeleafTemplateAdapter` | Subject + HTML |
| 19 | `NotificationIngestionService` | `notificationRepository.save(...)` | `notifications` | Rows in `PENDING` |
| 20 | `NotificationDeliveryScheduler` | `processDueNotifications()` every 5 s | `NotificationDeliveryService` | Phase B |
| 21 | `NotificationDeliveryService` | `stateService.claim(id)` | `notifications` | `PROCESSING` |
| 22 | `NotificationDeliveryService` | `emailSender.sendHtml(...)` | `SmtpEmailSender` | SMTP send |
| 23 | `SmtpEmailSender` | `mailSender.send(...)` | MailHog `:1025` | Deliver to sink |
| 24 | `NotificationStateService` | `markSent` / `recordFailure` | `notifications` | `SENT` / retry / permanent failure |

---

## 4. Data stores touched

| Database | Table | Written by |
|---|---|---|
| `appointment_db` | `appointments` | appointment-service |
| `appointment_db` | `appointment_history` | appointment-service |
| `appointment_db` | `operation_results` | appointment-service (idempotency) |
| `appointment_db` | `outbox_events` | appointment-service (publish + relay) |
| `appointment_db` | `reminder_publications` | appointment-service (reminders only) |
| `notification_db` | `processed_events` | notification-service (dedup) |
| `notification_db` | `notifications` | notification-service (jobs + state) |
| `notification_db` | `notification_templates` | migration seed (`V3`) |

---

## 5. Why the outbox pattern is used

Publishing to RabbitMQ inside the same code path as the booking would create a dual-write problem: the DB commit could succeed while the broker publish fails (or vice versa). The outbox decouples them:

1. The event row is committed **atomically** with the appointment.
2. A separate relay publishes it, retrying indefinitely with backoff.
3. At-least-once delivery is safe because the consumer deduplicates by `eventId` in `processed_events`.

Reminders use the same mechanism (`GenerateAppointmentRemindersService` inserts an `APPOINTMENT_REMINDER` outbox row, guarded by the `reminder_publications` uniqueness constraint so the same reminder is never published twice). Before publishing, `PublishOutboxService.currentReminder()` re-reads the appointment and **suppresses** the event if it was cancelled/rescheduled or its slot changed after the reminder was generated — preventing stale reminder emails.

---

## 6. Failure behaviour

| Failure | What happens |
|---|---|
| patient/doctor/auth service down during booking | Booking fails before the transaction (Feign error mapped to a typed `AppointmentError`); no appointment, no outbox row |
| auth recipient lookup fails during relay | `outbox.retry` with 30 s backoff, `DEPENDENCY_UNAVAILABLE`; row stays in outbox |
| RabbitMQ down / no confirm | `outbox.retry` with 30 s backoff, `BROKER_UNAVAILABLE`; row stays in outbox |
| Duplicate event delivery | `processed_events` check → ignored, `notification.events.duplicate` counter |
| Invalid event schema | Listener throws → Spring retry (3 attempts) → DLQ `notification.appointment.dlq` |
| SMTP failure | `notifications` → `RETRY_SCHEDULED` (1 m, 5 m) → `PERMANENT_FAILURE` after 3 attempts |
| Worker crash mid-send | Row stuck in `PROCESSING`; recovery scheduler resets it after 10 m |
| Recipient has no email | No notification row created; logged, not an error |

---

## 7. Quick local verification

```bash
# 1. Book an appointment (through FE or directly)
curl -X POST http://127.0.0.1:8083/api/v2/appointments \
  -H "Authorization: Bearer <token>" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"patientId":"<uuid>","doctorId":"<uuid>","slotId":"<uuid>","reasonForVisit":"..."}'

# 2. Inspect outbox progression
#    PENDING_ENRICHMENT -> READY -> PUBLISHED
psql -h 127.0.0.1 -p 5435 -U fclinic_admin -d appointment_db \
  -c "select id,event_type,status,attempt_count,last_error_code from outbox_events order by created_at desc limit 5;"

# 3. Inspect notification rows
#    PENDING -> PROCESSING -> SENT (or RETRY_SCHEDULED / PERMANENT_FAILURE)
psql -h 127.0.0.1 -p 5436 -U fclinic_admin -d notification_db \
  -c "select id,type,recipient_email,status,attempt_count,last_error_code from notifications order by created_at desc limit 5;"

# 4. View the email
xdg-open http://localhost:8025        # MailHog
xdg-open http://localhost:15672       # RabbitMQ management (fclinic_rabbit / rabbit_secret)
```

---

## 8. References

- Booking entry: `ClinicAppointmentFE/src/appointment/api.js:86`
- Controller: `services/appointment-service/.../api/controller/AppointmentController.java:52`
- Use case: `services/appointment-service/.../application/usecase/AppointmentApplicationService.java:45`
- Idempotency: `.../application/usecase/IdempotentAppointmentOperation.java`
- Outbox write: `.../infrastructure/adapter/OutboxAppointmentEventPublisher.java`
- Outbox schema: `services/appointment-service/src/main/resources/db/migration/V1__appointment_v2.sql:31`
- Relay: `.../infrastructure/adapter/OutboxRelayJob.java`
- Relay logic: `.../application/usecase/PublishOutboxService.java`
- Outbox SQL: `.../infrastructure/adapter/JdbcOutboxStoreAdapter.java`
- Broker publisher: `.../infrastructure/adapter/RabbitNotificationPublisherAdapter.java`
- Broker config: `.../config/RabbitMQConfig.java`
- Recipient lookup: `.../infrastructure/adapter/FeignAppointmentReferenceAdapter.java:84`
- Auth recipient endpoint: `services/auth-service/.../api/controller/AppointmentRecipientController.java`
- Auth recipient logic: `services/auth-service/.../application/service/AppointmentRecipientService.java`
- Consumer: `services/notification-service/.../infrastructure/messaging/AppointmentEventListener.java`
- Ingest: `services/notification-service/.../application/usecase/NotificationIngestionService.java`
- Delivery: `services/notification-service/.../application/usecase/NotificationDeliveryService.java`
- State machine: `services/notification-service/.../application/usecase/NotificationStateService.java`
- Template rendering: `services/notification-service/.../infrastructure/adapter/ThymeleafTemplateAdapter.java`
- SMTP: `services/notification-service/.../infrastructure/mail/SmtpEmailSender.java`
- Rabbit config: `services/notification-service/.../config/RabbitMqConfig.java`
- Template seed: `services/notification-service/src/main/resources/db/migration/V3__seed_notification_templates.sql`
- MailHog/RabbitMQ containers: `ClinicAppointmentBE/docker-compose.yml:196-227`
