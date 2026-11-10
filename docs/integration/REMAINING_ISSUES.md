# Remaining issues — latest main + Notification, 2026-11-09

**PARTIAL. Real local booking/UUID Notification and Patient regression work. See the self-service onboarding update and its current evidence below.**

## 1. Self-service Patient onboarding update — 2026-11-10

The former arbitrary-UUID linking blocker is addressed by an additive Patient-owned `patient_self_registrations` association. Creation and this association commit in one transaction, keyed by the verified Keycloak subject and serialized with a PostgreSQL transaction advisory lock. Retrying initial creation returns the same persisted Patient UUID. The immutable audit creator alone is not accepted as ownership proof.

Auth delegates the caller bearer to Patient `GET /api/v3/patients/self-profile`; Patient independently verifies identity and returns only that subject's active self-created profile. Auth rejects a different requested UUID, locks/rechecks the account, persists the binding and updates existing Keycloak attributes. Auth V3 adds a unique non-null Patient binding index. React recovers the server-owned profile after browser storage loss and verifies `/me` before updating its session.

Historical unlinked profiles created before this ownership association are preserved and are not automatically claimed from name, phone, email or creator audit fields. They require explicit owner-reviewed reconciliation. Existing identical trusted bindings remain usable. This change does not make Patient/medical data public or broaden Staff/Doctor WorkScope.

Runtime acceptance and current test evidence are recorded at the top of PATIENT_ACCEPTANCE_TESTS.md. Cross-service Keycloak/DB updates retain the existing rollback-and-retry pattern; there is no distributed transaction. Auth/Patient owners should review the additive ownership endpoint and uniqueness migration before team deployment.
## 2. Other remaining scope

- Doctor public create/profile-slot writes still lack user/Doctor-owner authorization. Protected internal references retain service credentials. This integration did not rewrite Doctor policies.
- Notification reminder send/retry against the latest Appointment reminder-reference/version remains not fully integrated/verified. Created/rescheduled/cancelled events and real delivery were verified.
- Broker also retains the additional queue `fclinic.appointment.notifications`: latest 2026-11-10 snapshot has 13 ready messages, while the current consumer queue `notification.appointment.q` has 0 ready/unacknowledged. It receives additional event copies and was not purged, rebound or replayed. Its owner must confirm retirement/migration to avoid an accumulating unused backlog. Current Notification delivery uses the working consumer queue; this extra queue is not counted as delivered data.
- Notification checks JWT signature/issuer/audience/expiry, account UUID and role. Immediate rejection of an otherwise unexpired JWT after logout/account disable was not verified for Notification; offline JWT validation is not equivalent to Auth introspection revocation. Revoked-token rejection on Patient/Appointment was verified.
- Browser wall-clock expiration, full unsaved-change Cancel branch, registration form and Admin CRUD not exhaustively rerun. Browser login/refresh/account switch, onboarding retry, booking/history and isolated Notification Drawer were exercised.

## Resolved this run

Main e94d652 merged with 2 conflicts resolved. Notification 316944c file hunks received; 860b433 legacy Auth/mock excluded. V4/V5 rehearsed on database copies, including one archived numeric fixture and eight preserved templates, then applied to the actual local Notification DB (initial notifications=0). No volumes/realm/DLQ reset. Old 3 DLQ messages retained; one malformed test event increased DLQ to 4. Real booking → outbox → RabbitMQ → UUID PostgreSQL → JWT REST → React and MailHog passed. Patient/clinical/version/security/provider-outage regression passed. All 8 application health checks passed.
