# Remaining issues — latest main + Notification, 2026-11-09

**PARTIAL. Real local booking/UUID Notification and Patient regression work. Automatic trusted onboarding is not complete.**

## 1. Auth/Patient ownership contract — owner review required

Main's PUT /api/v1/auth/me/patient-profile accepted a client-selected UUID without verifying Patient existence, ACTIVE state, ownership, unique account association or concurrent claims. It must not remain a public arbitrary linking shortcut.

The integration fails closed: new claims, other roles and replacement bindings return 403. Only acknowledging an identical existing trusted Patient binding succeeds, without DB/Keycloak writes. Regression/runtime tests cover this boundary. New users can register/create an initial profile but stop at trusted provisioning; React explains this instead of pretending success.

The complete booking acceptance used the existing ADMIN provisioning API for synthetic accounts after independently checking the exact creation principal, ACTIVE row and no prior account mapping. This operator check is not an automatic ownership contract or a DB uniqueness guarantee.

Auth/Patient owners must decide:

1. A trusted server-to-server ownership/existence/active-state proof, distinguishing Keycloak subject, Auth account UUID and Patient UUID. Email, phone, UUID possession and browser state are insufficient.
2. Enforced one-to-one account association and serialized concurrent claims, including administrative paths.
3. Durable create/link idempotency and recovery after response loss, logout, multiple tabs/devices and concurrent initial creates. Current account-scoped sessionStorage retains a confirmed UUID and prevents duplicate POST during retry in the same session; it is not exactly-once server provisioning.
4. Safe reconciliation for existing/inactive/missing profiles and partial Keycloak/DB failures. Do not require a MedicalRecord for booking.

## 2. Other remaining scope

- Doctor public create/profile-slot writes still lack user/Doctor-owner authorization. Protected internal references retain service credentials. This integration did not rewrite Doctor policies.
- Notification reminder send/retry against the latest Appointment reminder-reference/version remains not fully integrated/verified. Created/rescheduled/cancelled events and real delivery were verified.
- Broker also retains the additional queue `fclinic.appointment.notifications`: final snapshot has 9 ready messages, while the current consumer queue `notification.appointment.q` has 0 ready/unacknowledged. It receives additional event copies and was not purged, rebound or replayed. Its owner must confirm retirement/migration to avoid an accumulating unused backlog. Current Notification delivery uses the working consumer queue; this extra queue is not counted as delivered data.
- Notification checks JWT signature/issuer/audience/expiry, account UUID and role. Immediate rejection of an otherwise unexpired JWT after logout/account disable was not verified for Notification; offline JWT validation is not equivalent to Auth introspection revocation. Revoked-token rejection on Patient/Appointment was verified.
- Browser wall-clock expiration, full unsaved-change Cancel branch, registration form and Admin CRUD not exhaustively rerun. Browser login/refresh/account switch, onboarding retry, booking/history and isolated Notification Drawer were exercised.

## Resolved this run

Main e94d652 merged with 2 conflicts resolved. Notification 316944c file hunks received; 860b433 legacy Auth/mock excluded. V4/V5 rehearsed on database copies, including one archived numeric fixture and eight preserved templates, then applied to the actual local Notification DB (initial notifications=0). No volumes/realm/DLQ reset. Old 3 DLQ messages retained; one malformed test event increased DLQ to 4. Real booking → outbox → RabbitMQ → UUID PostgreSQL → JWT REST → React and MailHog passed. Patient/clinical/version/security/provider-outage regression passed. All 8 application health checks passed.
