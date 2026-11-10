# Full application integration

Status: **PARTIAL — real Patient and cross-service acceptance verified; full product gates remain open**.

## Application

One index.html → src/main.jsx → App.jsx → AuthProvider. Existing role landing pages retained: AdminUsersPage, StaffWalkinPage, DoctorDashboardPage and DoctorDirectory. Patient management is reachable for STAFF/DOCTOR; a linked PATIENT can open their own profile. Existing booking/history adapters use Appointment v2, Patient v3 and Doctor UUID resources.

Navigation uses one host tab state with hash URLs. Patient subviews carry view/patientId/recordId for refresh/deep links; forms guard unsaved edits and unload. Account/role/business binding changes remount protected state. Logout clears credentials and pending commands before the network call finishes.

## API and authorization

- Auth /api/v1/auth: register/login/me/refresh/logout; admin account/role/status; new authenticated GET /me/patient-capabilities supplies display hints from existing WorkScope evaluation.
- Patient /api/v3/patients: create/search/get/replace and create/list/get/replace medical records.
- Doctor /api/v1/doctors: directory/detail/slots/schedule. Protected /internal/v1/doctors projections use distinct provider credentials.
- Appointment /api/v2/appointments: availability, list, detail/history, book/cancel/reschedule; idempotency keys and versions retained.
- Vite /api → Gateway :8080 → Eureka registered services. Auth :8085 uses TLS and verified localhost identity; other loopback provider HTTP follows the existing contracts.
- Auth public registration cannot bind a caller-chosen Patient UUID. Google login requires verified OIDC code exchange. OTP defaults to mail without returning OTP in HTTP.

## Verification

Full reactor: PASS, 367 tests (1 Gateway + 13 Auth + 294 Patient + 9 Doctor + 29 Appointment + 21 Notification), 0 failure/error/skipped. Patient reactor: PASS. Frontend: 41 tests PASS, production build PASS. Cross-service contract script: PASS. See test-counts.json and local .log files.

Notification remains a genuine provider-contract blocker: numeric account/appointment persistence and an unprotected REST controller do not satisfy current UUID ownership/security. Legacy pages were preserved; their retired mutation methods reject rather than invoking retired v1 endpoints or faking success. They are not alternative entrypoints.

## Real local evidence (final)

24/24 Patient/Auth HTTP checks; 11/11 cross-service Gateway checks; 7/7 authorization checks; 3/3 outage checks PASS. All seven JAR health endpoints UP. PostgreSQL: two synthetic profiles and two medical records; unauthorized Patient mutations and Doctor outage did not change protected database state.

Browser: Staff login → Patient create/read/update; Doctor login → profile/records → medical create/update with exact granted walk-in visit → refresh/deep link; Doctor appointment association → Patient → browser Back; logout removed protected data. Screenshots are stored alongside this report. Notification drawer correctly reports its provider UUID blocker.

Eureka advertised hostname was corrected through the original startup environment to localhost; Windows mshome.net hostnames had caused Gateway DNS failures. No TLS/auth validation was disabled. The last full backend verify predates only frontend/runbook/acceptance changes; backend source has not changed since that successful run.

## Current reception/clinical verification — 2026-11-10

Supersedes older runtime/blocker statements above: eight canonical Patient operations passed with actual Staff/Doctor React editors and persisted PostgreSQL. Auth adds only non-authoritative readRecordPatientIds hints, evaluated through exact active MEDICAL_READ assignments; React separates read navigation from clinical mutation permissions. Existing Patient backend policies remain authoritative and unchanged. Full Maven 383 tests, Patient-only verify, 13 contracts, React 51 tests/build pass. Current HTTP/runtime latest attempts 131/131 pass; the initial Notification discovery 503 is retained and fixed with Gateway route readiness. Cold restart + browser refresh preserves exact profile/record/audit data. Patient version 6 with 7 revisions; MedicalRecord version 2 with 3 revisions. Notification UUID/recipient delivery works; consumer queue drained, 4 DLQ messages retained, extra legacy queue retains 14 copies.

See PATIENT_ACCEPTANCE_TESTS.md for the current case IDs, browser images, individual APIs and limits. WorkScope deployment assignment for a new Staff-created Patient and absence of an Appointment completion operation remain explicit boundaries; no completion, authorization or notification success is fabricated.

## Staff reception contract extension — 2026-11-10

Problem: fixed UUID deployment grants let Staff create a Patient but rejected the immediate profile GET and omitted existing unassigned profiles from search. Clinical policy was not the cause and remains unchanged.

- Patient GET /api/v3/patients/reception-lookup: authenticated active Staff, existing administrative SEARCH macro, explicit name/code keyword >=2 or valid phone, size <=20. Only actual returned Patient UUIDs are assigned before returning data. Empty global lookup is rejected (400); Doctor/Patient callers are forbidden (403).
- Canonical Patient GET /api/v3/patients/{id}: reception-enabled Staff must hold the administrative READ macro; Patient verifies row existence, asks Auth for exact assignment, then runs the original per-Patient policy. Dependency failure is closed (503), preserving an already-created profile for safe read retry. POST create remains a normal database write, without a second distributed side effect.
- Auth POST /api/v1/auth/internal/reception-assignments: contract patient-reception/1, dedicated Basic client patient-reception and separate secret, HTTPS, UUIDv4 nonce, fresh requestedAt, exact verified caller bindings, active real Staff account, explicit READ/SEARCH desk macro. Browser bearer and introspection credentials are rejected (401). This is a Patient-owned provider contract extension requiring Auth/Patient owner review before shared deployment.
- Auth V4 reception_patient_assignments stores unique user/patient/source rows with internal users FK and external Patient UUID. SQL limits operations to administrative/appointment rights. Upsert is idempotent, transactionally planned, and preserves the earliest validFrom. PostgreSQL microsecond precision is normalized before persistence. Permission expiry is the smaller of eight hours and the source desk grant expiry; source removal/revocation/expiry invalidates derived rows. No MEDICAL_* or VALID_VISIT is derived, and no allPatients wildcard is used.
- Search combines valid finite matching grants. React refreshes backend capabilities after profile read, ignores stale capability responses, and keeps a stable API client so refresh does not generate a request loop.
- The existing launcher enables reception mode and configures a distinct ignored secret using existing trusted TLS. There is no alternative app, API server, database or configuration family.

New regression coverage includes real disposable PostgreSQL migration/persistence/revocation/idempotency tests in Auth; Patient orchestration and real PostgreSQL scope boundaries; frontend route selection and capability-refresh races. Real runtime evidence is in staff-reception-evidence.json, with synthetic UI screenshots staff-reception-created-profile.jpg and staff-reception-updated-profile.jpg. Existing clinical grants and account binding are separate; a profile created by Staff can be booked without a Patient account or any MedicalRecord.

## Doctor slot overlap fix — 2026-11-10

Doctor POST /api/v1/doctors/{id}/slots previously validated the one-hour duration but saved overlapping rows. The canonical JpaTimeSlotRepositoryAdapter now locks the owning Doctor row with PostgreSQL SELECT FOR UPDATE within the same transaction as checking and flushing the slot write. This serializes concurrent application writers across instances, including the first slot for a Doctor. It checks same Doctor/date and intervals existing.start < requested.end AND requested.start < existing.end, excluding the same row UUID for updates. Booked/unpublished rows are not silently ignored. Adjacent intervals, different days and different Doctors remain allowed. Existing one-hour validation remains unchanged.

A conflict returns HTTP 409 with stable code DOCTOR_SLOT_OVERLAP and a Vietnamese explanation. The React creation modal retains the error inline (role=alert), preserves form values, and only closes on confirmed success. Requests are also guarded while a creation is pending. Database schema and existing slot IDs were not changed. This protects writes through the application's canonical persistence adapter; external/manual SQL writers must use equivalent serialization and validation.

Verification: Doctor -am Maven verify PASS (13 tests: 9 existing, 4 new real embedded-PostgreSQL tests covering duplicate, adjacency/isolation, legacy partial overlap/containment and simultaneous first inserts). Cross-service contracts 13/13 PASS. Frontend 53/53 tests PASS and build PASS. Full reactor was last verified before this focused Doctor change; this change reran the affected module and contracts, not another full reactor.

Runtime JAR/Gateway/PostgreSQL checks: Test-DoctorSlotOverlap.cjs 12/12 PASS; 8 concurrent identical POSTs produce exactly one 201 and seven 409/DOCTOR_SLOT_OVERLAP; PostgreSQL contains exactly one contended row. Adjacent/other-day/other-Doctor creation passes, sequential duplicate fails. See doctor-slot-overlap-evidence.json. React Doctor login -> Lịch làm việc -> Thêm ca trực -> 09:00-10:00 already present -> persistent conflict message, form stays open. See doctor-slot-overlap-ui.jpg. The existing startup script restarted managed apps, retained Docker and all volumes, and passed Gateway route readiness (Appointment needed 29 attempts).

One pre-existing overlap pair remains in the real Doctor database (16:00-17:00 on 2026-11-10). It was preserved to avoid deleting/reassigning UUIDs that may be referenced by appointments. The HTTP test confirms no new overlap pairs were added. Reconciliation of this old pair requires checking Appointment references before retiring a specific slot; this fix does not erase history.

Logs (ignored .runtime): doctor-overlap-verify.log, doctor-overlap-contracts.log, doctor-overlap-frontend-tests.log, doctor-overlap-frontend-build.log, doctor-overlap-start.log. Test-only API fixtures are synthetic and stored in the canonical Doctor database. No auth bypass, replacement database, Docker reset or GitHub push.
