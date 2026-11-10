# Patient acceptance — current self-service onboarding, 2026-11-10

**Self-service profile creation: PASS (real HTTP, PostgreSQL and React browser).**

- Canonical persistence: `patient_db.patients` plus one `patient_revisions` row. Ownership is stored in additive `patient_self_registrations`; account linkage is in `auth_db.users.patient_id`. No profile is stored in medical_records or in a replacement database.
- API acceptance: **34/34** checks in `self-onboarding-evidence.json`. Register/login → concurrent initial creation returns the same UUID → lost-response retry → verified self-owned linking → /me → refresh/logout/re-login. Cross-account claim/read, arbitrary UUID and rebind are denied. The record and owner association were checked using PostgreSQL queries.
- Browser: new synthetic Patient logged in, completed the onboarding form, saved, automatically reached Doctor Directory, refreshed, then opened its actual Patient detail. UUID `ce88f75b-442b-4d3c-b8aa-5e4107e65b34`, version 0, entered date/address persisted. Evidence: `self-onboarding-browser.jpg`. No manual ADMIN provisioning was used for either new account.
- Runtime extension: **24/24** checks in `self-onboarding-runtime-evidence.json`: browser form fields and ownership in PostgreSQL, Auth V3 applied, both automatically linked Patients book real CONFIRMED appointments without MedicalRecords, booking/history persists, and the browser-created Patient receives its actual UUID Notification.
- Fixture correction: the first API-only profile omitted optional contact email. Its booking succeeded; Notification correctly skipped the Patient email and delivered the Doctor notification. That original booking was retained. The browser profile includes account email and its separate genuine booking produced the correct Patient notification. No consumer change, fabricated success or event replay was used.
- Health: **8/8 UP**, `self-onboarding-health.json`; Auth HTTPS checked with the trusted CA certificate, not SSL bypass. Current consumer queue is drained; DLQ remains 4 with no replay/purge, and the additional legacy queue has 13 ready copies. The first attempt after the overnight interruption found Docker/services stopped; Docker Desktop and the same stack were restarted before acceptance.
- Backend full verify: **382 tests**, zero failures/errors/skips (Gateway 1, Auth 17, Patient 296, Doctor 9, Appointment 29, Notification 30). Patient-only verify PASS; security contracts **13/13** PASS. Frontend **47 tests / 10 files PASS**, production build PASS. Logs are retained in ignored `.runtime/self-onboarding-*.log`.

Repeat with healthy services and synthetic data only:

```powershell
node docs/integration/Test-SelfOnboarding.cjs
# After creating the retained browser test account's profile via React:
node docs/integration/Test-SelfOnboarding.cjs runtime
```

Historical unlinked profiles lacking the new ownership association are preserved for explicit owner-reviewed reconciliation; UUID/name/phone/email alone never permits a claim. Existing trusted linked accounts continue to work. Staff/Doctor finite grants and VALID_VISIT policies are unchanged. Existing clinical regression evidence below remains historical; browser registration, full expiration timing and Admin CRUD were not rerun in this change.

## Historical integration acceptance — 2026-11-09

The following describes the earlier fail-closed implementation; its automatic-link blocker is superseded by the verified update above.

**Overall PARTIAL: ordinary self-service account linking is blocked safely; operator-provisioned booking and UUID Notification work.** All data is synthetic; services/providers/databases are real.

| Flow | Result | Evidence |
|---|---|---|
| A. Register → profile | PASS HTTP/profile UI; PARTIAL complete onboarding | New Patient starts unlinked, POST v3 creates profile. Browser B creates once and retries linking without another row. |
| Trusted account linking | PARTIAL | Public arbitrary linking denied. Existing ADMIN provisioning used only after independently verifying creation principal, ACTIVE profile and no existing account mapping. Not automatic onboarding. /me, refresh/re-login preserve Patient UUID. |
| B. Booking without MedicalRecord | PASS for trusted linked account | Doctor/published Slot UUID, CONFIRMED persistence, history, idempotency, duplicate conflict, reschedule/cancel. SQL verifies zero medical records for this Patient throughout. |
| C. Notification | PASS tested lifecycle | Real booking outbox → RabbitMQ → UUID rows → personal JWT REST → React; six SMTP deliveries for create/reschedule/cancel and MailHog receipt. Redelivery dedup and malformed isolation verified. |
| D. Existing Patient/clinical | PASS | Finite Staff search/read/update, Doctor read and new MedicalRecord create with exact VALID_VISIT; update/version/revision, unauthorized DB invariance and provider outage. |
| E. Negative | PASS executed cases; PARTIAL exhaustive/browser timing | Unlinked/wrong Patient/Doctor, missing grant/token, invalid/revoked token, UUID, absent references, elapsed/occupied slots, stale version, cross-user Notification/admin denial, malformed event. Wall-clock browser expiration not waited. |

## Current evidence

- onboarding-notification-evidence.json: **54/54**. An initial B registration used a duplicate synthetic phone and returned 409; fixture corrected to a distinct phone, assertions unchanged. Initial attempt preserved in ignored .runtime.
- notification-broker-acceptance.json: **7/7**. DLQ **3 → 4** after one deliberate malformed event. Old messages untouched; no replay/reset. Six lifecycle rows SENT, three Patient emails observed in MailHog.
- appointment-edge-evidence.json: **11/11**: absent Doctor 404; missing slot 409; malformed UUID 400; missing Appointment 404; elapsed published slot 409; revoked token booking 401; rejected writes leave Appointment count unchanged.
- runtime-http-evidence.json: **25/25** Patient checks including a new Doctor-created MedicalRecord for the separate previously scoped synthetic Patient, after the new-Patient booking acceptance.
- authorization-negative-evidence.json: **7/7**; unauthorized-database-evidence.json unchanged=true.
- provider-outage-evidence.json: **3/3**; outage-database-evidence.json medical record count **3 → 3**; Doctor restored.
- browser-database-evidence.json: B profile rows=1 after retry; A medical records=0; two A bookings, one CONFIRMED from React.
- Final read-only snapshot after browser booking: Flyway V1–V5 successful; 8 global Notification rows SENT (4 personal rows for A); current consumer queue 0 ready/0 unacknowledged, DLQ 4/0. Additional preserved queue fclinic.appointment.notifications has 9 ready messages and is an owner-cleanup item, not consumer success evidence.
- sync-runtime-health.json: Discovery/Auth HTTPS/Patient/Doctor/Appointment/Notification/Gateway/React **8/8**. Health alone is not the E2E conclusion.

## Browser observations and retained screenshots

Login A → real Doctor → 11 November 13:00 slot → confirm → persisted CONFIRMED booking → history CREATED/PATIENT/version 0. Notification Drawer shows four actual SENT rows only for A. Refresh keeps verified identity. Logout clears protected UI. Login B opens onboarding; failed linking/retry retains one profile; B inbox is empty. Logout B/re-login A restores A's account/history. Browser left at http://127.0.0.1:5173/#history.

- patient-booking-browser.jpg
- notification-uuid-browser.jpg
- onboarding-trusted-provisioning-browser.jpg

## Automated quality

Reactor verify **379 tests**, 0 failures/errors/skips: Gateway 1, Auth 15, Patient 295, Doctor 9, Appointment 29, Notification 30. Patient-only verify PASS. **13** contracts PASS. Frontend **46 tests / 10 files PASS**, build PASS. Logs: sync-backend-final.log, sync-patient-verify.log, sync-contracts.log, sync-frontend-final.log and sync-frontend-build.log (ignored). Counts: test-counts.json. No skipped tests or runtime mock success.

Public linking is not described as safe autonomous onboarding. Registration UI, wall-clock expiry, full unsaved-change Cancel branch and Admin CRUD were not all rerun in browser. Notification reminders/latest-reference retry and immediate JWT revocation are not fully verified.

## Medical-record read-only UI correction — 2026-11-10

Patient self-read was permitted by backend, but the records list incorrectly displayed a blocker whenever CREATE permission was absent. Removed that warning from read views and hid create/edit controls for read-only users. Real browser self-read now shows the successful empty state (0 records), rather than a permissions warning. Detail read has the same separation from write permission. Backend access policies remain unchanged. Two regression tests cover empty-list and detail read-only rendering; frontend 49 tests / 11 files and production build PASS. Browser proof is retained only in ignored .runtime because it contains the user's displayed profile details.
