# Patient acceptance — 2026-11-09 latest main/Notification

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
