# Patient acceptance — Staff reception to clinical record, 2026-11-10

**Latest Staff fix: PASS — reception lookup, create/read/update, durable exact grants, browser and PostgreSQL checked after final rebuild. See Staff profile access regression and Final rerun evidence below.**

**Canonical Patient APIs: 8/8 PASS with real services, real PostgreSQL and explicit finite WorkScope.** All identity, demographic and clinical input for this run is synthetic. No runtime mocks, fake success, authorization bypass or database reset was used.

The same case was exercised through React: Staff logs in → creates Patient → receives an exact administrative assignment → searches/selects that Patient at reception → selects actual Doctor/Slot UUIDs → books and reads the persisted appointment → treating Doctor opens the Patient from the appointment list → creates MedicalRecord with exact VALID_VISIT and Appointment reference → amends it with version/changeReason. A different assigned read-only Doctor and the linked Patient subsequently read the same amended record. Staff separately updates the administrative profile through React.

| Patient v3 operation | Result | Evidence |
|---|---|---|
| POST /api/v3/patients | PASS | Staff creates through React; canonical row, immutable Staff subject and initial revision verified by SQL. Additional Patient self-create/retry also returns 201 and one stable UUID. |
| GET /api/v3/patients | PASS | Name, patientCode and phone searches; exact finite result; first/next page metadata. |
| GET /api/v3/patients/{id} | PASS | Staff, assigned Doctor and own Patient read actual data; wrong Patient/scope denied. |
| PUT /api/v3/patients/{id} | PASS | Live API and Staff React editor; readback; optimistic version; no-op; INACTIVE/ACTIVE transitions; revisions. |
| POST /api/v3/patients/{patientId}/medical-records | PASS | Treating Doctor React editor persists one record with actual Doctor/Appointment UUIDs and explicit exact VALID_VISIT. |
| GET /api/v3/patients/{patientId}/medical-records | PASS | Doctor/read-only Doctor/own Patient list actual record; pagination; other callers denied. |
| GET /api/v3/patients/{patientId}/medical-records/{recordId} | PASS | Same persisted content/version read independently by authorized accounts; absent record 404. |
| PUT /api/v3/patients/{patientId}/medical-records/{recordId} | PASS | Doctor React amendment plus API amendment; changeReason persisted in revision; stale version 409; no-op preserves version. |

Retained case:

- Patient UUID: `221a006a-6843-40b3-89d3-6a3faea21d43`.
- Doctor UUID: `c6f4f54e-1d05-4c6e-8392-9da5092ea4d7`.
- Appointment UUID: `f63a85ec-4e90-4b1b-9dad-ee154099ce80` (actual booking remains CONFIRMED).
- MedicalRecord UUID: `aef9dac7-2821-4ec3-b2c3-fbde78304a78`.
- Final profile version **6**, profile revisions **7**. Final record version **2**, medical revisions **3**; exactly one record for this Appointment.
- Canonical tables: patient_db.patients, medical_records, patient_revisions, medical_record_revisions; trusted identity association in auth_db.users. No Patient data was moved to another schema/database.

Negative/runtime coverage includes missing/invalid/revoked token, wrong Patient, Doctor without exact scope, wrong Doctor UUID, malformed UUID, future visit, missing changeReason, stale version, duplicate Appointment medical record, inactive Patient, unauthorized Staff/Patient/read-only Doctor writes and Doctor provider outage (503). SQL snapshots prove rejected writes preserve rows/revisions. appointmentId=null with no walk-in VALID_VISIT is explicitly denied. Self-profile ownership and booking Notification recipient isolation also pass.

Source fixes found by this flow:

1. Auth capabilities now expose additive readRecordPatientIds derived from actual MEDICAL_READ grants. React uses this for clinical navigation, independently from create/update rights; own Patient self-read follows verified identity. Staff no longer sees clinical links simply because administrative profile read succeeded. Backend access policy is unchanged.
2. Start-FClinic.ps1 now waits for actual Gateway routes, including expected 401 on protected routes, after Actuator UP. The first post-build extension attempt correctly failed with Notification 503 while Gateway's discovery cache contained only 3 services. This historical failure remains in reception-clinical-evidence.json. Unchanged assertions passed after route-readiness verification; a full cold restart passed: Gateway waited 29 probes for Appointment discovery before reporting readiness, and all routes became reachable.

Verification: full Maven verify **383 tests**, zero failures/errors/skips (Gateway 1, Auth 18, Patient 296, Doctor 9, Appointment 29, Notification 30); Patient-only verify PASS; security contracts **13/13** PASS; React **51 tests / 11 files PASS**, build PASS. Logs are retained in ignored .runtime/reception-*.log. reception-clinical-evidence.json preserves every attempt and separately summarizes the latest attempt per phase; the first 503 failure is not deleted or counted as a final passing assertion.

Latest attempts: **131/131 checks PASS**; one historical 503 failure retained. A cold restart preserved the exact PostgreSQL rows and audits; the Patient refreshed the browser and read the same version 2 record. All 8 local health checks are UP (reception-clinical-health.json); Auth TLS was checked with the actual CA certificate. Current Notification consumer queue is drained, DLQ remains 4 untouched, and the additional legacy queue retains 14 copies.

Browser evidence: reception-staff-profile.jpg, reception-staff-booking.jpg, reception-doctor-created-record.jpg, reception-staff-updated-profile.jpg, reception-reader-doctor-record.jpg, and reception-patient-record.jpg after the cold restart. These contain synthetic test data only.

Boundaries: WorkScope assignment is still protected deployment configuration, not an Admin UI workflow. A new Staff-created UUID required explicit exact assignment before it could be read/updated or booked; the initial 403 before assignment was observed and respected. Synthetic Patient account association used the existing trusted ADMIN API after verifying the exact Staff-created row and no prior account binding. Grants are finite, no wildcard, and expire after 8 hours. Appointment currently exposes booking/cancel/reschedule/read, not a completion operation; writing MedicalRecord does not claim that Appointment changed to COMPLETED. The synthetic encounter uses the explicitly granted past visit timestamp; booking slot start is 13:00 and the test does not simulate waiting for real clinical care.
## Previous self-service onboarding acceptance — 2026-11-10

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

## Staff profile access regression — 2026-11-10 (current)

The earlier manual per-Patient UUID assignment limitation is resolved for Staff with an active finite reception desk assignment. Clinical permissions remain separate.

Real React browser checks: Staff login -> reception searches Synthetic Self and finds two previously existing profiles -> Patient Management -> create QA Staff UI Access 20261110 -> automatic detail opens with enabled Edit, no 403 -> change address -> save -> version 1 -> browser reload preserves profile/session and Edit. No per-Patient configuration edit, Auth restart or re-login occurred between creating and editing. Canonical Patient UUID: f8ab36f8-eb81-4565-b7be-27cf88a8b2b2. Screenshots: staff-reception-created-profile.jpg, staff-reception-updated-profile.jpg (synthetic data only).

HTTP/PostgreSQL regression script Test-StaffReception.cjs verifies immediate create/read/update, existing-profile lookup, finite search union, account-free confirmed booking, exact durable grants after Auth restart, and negative cases: unrestricted/short/oversized lookup, non-Staff lookup, clinical access, cross-Patient access, browser grant provisioning and wrong service credentials. Public results are appended to staff-reception-evidence.json; private tokens/secrets/state remain ignored .runtime. No database, realm or queue was reset.

### Final rerun evidence — 2026-11-10 15:40 +07

| Check | Result |
|---|---|
| Full Maven reactor verify | PASS: 391 tests, zero failures/errors/skips (Gateway 1, Auth 24, Patient 298, Doctor 9, Appointment 29, Notification 30). Completed 15:32:28. |
| Patient-only verify -am | PASS: 215 unit + 83 integration tests, completed 15:34:46. |
| Run-Contracts.ps1 | PASS: 13 contract checks. |
| npm test / npm run build | PASS: 53 tests across 11 files; Vite build PASS. |
| Managed cold restart | PASS: Start-FClinic.ps1 -SkipInfrastructure -Frontend, existing Docker/volumes retained. All Gateway readiness probes passed; 8/8 component HTTP health checks passed, Auth CA verified. See staff-reception-health.json. |
| Staff HTTP / PostgreSQL | Latest verify phase 34/34 PASS; post-restart persistence 11/11 PASS. Both browser-fixture SQL/HTTP checks 10/10 PASS each. |
| React after final rebuild | PASS: login Staff, find two existing Synthetic Self profiles, create a fresh profile, immediate detail + Edit enabled, save changed address, refresh -> version 1 and same data. No per-Patient assignment edits, service restart or re-login between create/edit. |

Fresh final browser Patient UUID: 24a0c7aa-0a17-4e25-95a0-49dce5263c86, phone +84900000963, synthetic email staff.ui.restart.20261110@example.test. Canonical PostgreSQL row and Auth exact assignment were independently checked. Screenshot: staff-reception-after-rebuild.jpg. Existing earlier UI fixture f8ab36f8-eb81-4565-b7be-27cf88a8b2b2 also survived the full restart.

Audit note: staff-reception-evidence.json deliberately retains one failed repeat-test assertion (Version advances). That attempt submitted an unchanged address: backend correctly returned 200 without incrementing version. The script now supplies a distinct address each run and still asserts exactly oldVersion+1; the subsequent complete verify phase passed all 34 checks. The accumulated file has 118 checks, 117 passing, one historical fixture failure; it must not be interpreted as 118 successful checks. An initial ad hoc health HTML assertion also treated PowerShell lines as an array; the corrected joined-string check passed for the live frontend. No production assertions or authorization policies were weakened to obtain passing results.

Logs retained locally (ignored .runtime): staff-reception-final-reactor.log, staff-reception-patient-final.log, staff-reception-contracts.log, staff-reception-frontend-final.log, staff-reception-frontend-build.log, staff-reception-final-start.log. Auth backup before V4: auth-before-reception-grants-20261110-140802.dump. Secrets/tokens are excluded from reports. Original workspace, Docker data, realm and DLQ were preserved. No GitHub fetch/merge/push was performed in this focused Staff fix.
