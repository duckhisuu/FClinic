# Conflict resolution — e94d652 + reviewed Notification UUID

Main merge a608f6229a856054cd39eaf3ecfaced874a75ce1: **2 conflicting files**, no remaining unmerged paths. Base/ours/theirs were inspected; no blanket selection.

- App.jsx: kept current capabilities, Patient session/guard, hash navigation, Patient links and role-scoped menus; incorporated main's user-aware defaults and PatientOnboardingPage. Menus/restoration use the full verified user so unlinked Patients get onboarding. Kept both history callbacks: open Patient and require profile. Booking callbacks now use the same hash navigation.
- AppointmentHistory.jsx: retained onOpenPatient and incorporated onRequireProfile; both functions remain available. Existing Doctor/Staff/Patient read/ownership rules still belong to backend.

Semantic conflicts and fixes:

- Main's self-link endpoint accepted any browser UUID. New claims/rebinding now fail closed with 403 pending an Auth/Patient ownership contract; identical trusted binding is an idempotent read. New regression and runtime authorization checks pass.
- Onboarding retains a confirmed created UUID in account-scoped sessionStorage before linking, retries that UUID without POSTing another profile, then reads /me before session update. Session updates reject an account switch/logout race; capabilities refresh when patientId changes. No durable cross-session/response-loss idempotency is claimed.
- Notification backend hunks came from 316944c, whose module parent was identical to main's current module. UUID DTO/JPA/ports/repositories, schema3 validation, recipient isolation, JWT security, V4/V5 and tests received; no older Auth/Doctor/Appointment/Gateway source copied.
- Canonical FE HTTP/session adapter retained. Personal inbox uses JWT-derived /me with no caller userId; no admin fallback. Admin retry controls remain role-gated; errors propagate. All received security tests are tracked despite legacy ignore rules.
- Only the reviewed Keycloak audience mapper was added to the existing realm and startup; no realm import reset, fake login or TLS bypass.

Main sync completed before Notification edits. Two local histories remain intact. Logs/evidence and limitations are in PATIENT_ACCEPTANCE_TESTS.md and REMAINING_ISSUES.md.
