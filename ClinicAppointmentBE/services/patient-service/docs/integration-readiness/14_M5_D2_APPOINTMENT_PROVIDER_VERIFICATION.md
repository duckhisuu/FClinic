# M5-D2 Appointment UUID, protected B-05 and secure B-02

24 October 2026. Decisions are PROJECT_DECIDED; actual implemented/verified levels are reported separately. Final machine evidence: [m5-d2-appointment-provider-verification.json](../../evidence/m5-d2-appointment-provider-verification.json).

## Baseline and safety

main / HEAD238e1dd36cb759df444cf9441ef0012f0d12d2b5 is only the base commit; changes remain uncommitted. Patient M5-D1 source manifest b96454a5aabd1354d0652f5606bad29d51324d4aa97214fe50ac1d799f026ec4 and Doctor manifest29459d93251a70c22fcce101dafcc78d96de0be2f7f773b057592d99353e5fef had zero drift at discovery. Initial Appointment pom+src manifest a060e2e185e408d8f1272a7176b9fa279cf9f70fa95569c55b1da36ce8dcf4a7; initial verify succeeded with zero tests.

Patient source/tests/POM, approved artifacts and Doctor source/tests/POM remain hash-unchanged in this task. Appointment code and new documentation are the changes. The evidence contains initial/final manifests and changed-file list, not a claim that dirty code is in HEAD.

## Implemented Appointment decisions

PAT-APT-V2-001 converts the target aggregate, command, entity, repositories, response and outbox event to UUID Patient identity; Appointment/Doctor/slot remain safe Long. Fresh PostgreSQL UUID schema, validate-only startup, no seed, no Long conversion/backfill and no v1 writer. See [UUID/lifecycle design](../../../appointment-service/docs/integration/APT_V2_UUID_AND_LIFECYCLE.md).

PAT-APT-LIFE-001 follows pack FUNCTIONAL_SPEC and existing createNew: new authorized booking CONFIRMED; explicit terminal domain transitions to CANCELLED/COMPLETED/NO_SHOW. Historical PENDING remains quarantined, not mapped/dropped. B-05 returns generic500 for it. NO_SHOW conveys no clinical encounter authorization. No new public exceptional workflow was invented.

PAT-REF-APT-001 is implemented by [minimum controller](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/reference/AppointmentReferenceController.java):14, [direct filter](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/reference/AppointmentReferenceFilter.java):8 and [effective-role startup guard](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/reference/ReferenceDatabaseGuard.java). Fixed marker, dedicated form-encoded Basic, TLS1.2/1.3, exact selected404, strict facts. The profile only exposes minimum read and redacted health; credential cannot list/book/cancel/read contact or outbox. Enabled invalid settings fail startup, disabled reference503.

B-05 schema remains b2ce460335e273e227b45e982c6cefb2512d2625f6f7ff05f2342d29e34c1e86. B-04 Doctor schema remains098d3651fe4bc5daad3ec813ce459acfc624a0888ad209f789aa6a4bcd08c172. No Patient decoder was relaxed.

## Actual B-05 verification

AppointmentRealProviderIT provisions fresh native PostgreSQL16.10 and synthetic records, distinct SELECT-only app role, ephemeral CA/server PKCS12, then starts the actual executable Spring Boot JAR via java -jar. Patient production Appointment reference adapter is invoked by the unchanged opt-in real-provider harness. This is not an Appointment HTTP double.

The final deployment record includes actual PID/start time, JAR SHA-256, full dirty source manifest, redacted configuration hash, certificate/CA hashes, endpoint hash and deployment UUID. pg_stat_activity observes appointment_reference_app sessions; actual permissions are checked with all write/elevated/schema-CREATE flags false. A separate actual binary launch using JDBC ?user=postgres is rejected by the effective-role guard. Full appointment snapshot and outbox count remain unchanged. Processes/owned PG are stopped; private PKCS12 files removed.

| Harness vector | Expected / actual upstream | Patient production consumer |
|---|---|---|
| VALID | 200 / 200 | Exact minimum UUID/status facts preserved |
| CANCELLED | 200 / 200 | cancelled=true; clinical application would reject422 |
| PATIENT_MISMATCH | 200 / 200 | Well-formed differing UUID retained; no substitution |
| DOCTOR_MISMATCH | 200 / 200 | Well-formed differing Doctor retained |
| MISSING | selected404 / 404 | Optional.empty |
| INVALID_CREDENTIAL | 401/403 / 401 | Typed dependency503; no resource disclosure |
| WRONG_VERSION_REQUEST | 400/406 / 406 | UNSUPPORTED_REFERENCE_CONTRACT, no resource data |
| TECHNICAL_5XX (persisted PENDING) | 5xx / 500 | Typed dependency503; no invented state |

All eight configured vectors must PASS in the accepted final run. The read-only harness does not execute a real Patient clinical mutation; application422 semantics remain component evidence, not a new real clinical E2E claim. UNKNOWN_STATUS-as-wire200, WRONG_RESOURCE, WRONG_VERSION_RESPONSE and INVALID_PAYLOAD optional fault vectors are NOT EXECUTED. PENDING is tested as provider500, not mislabeled as unknown200. Separate actual-binary TLS chain/hostname/plaintext and effective-role negatives are covered by AppointmentRealProviderIT.

Reproduce from root:

~~~powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/appointment-service -am verify -B -ntp
~~~

The IT invokes the existing Patient -P real-provider-contracts verify command with only B-05, dedicated ephemeral credentials, external truststore and genuine deployment evidence. Reuse [runbook12](12_REAL_INTEGRATION_RUNBOOK.md) for externally provisioned environments and [Appointment provider configuration](../../../appointment-service/docs/integration/APT_REF_V1_IMPLEMENTATION.md). No production fault endpoint or authentication/TLS bypass is used.

## B-02 implementation and verified limit

[SecureBookingService](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/application/usecase/SecureBookingService.java):18 authenticates original human and checks trusted self linking BEFORE Patient SERVICE lookup, Doctor/slot lookup or lock. It consumes full Patient v2 fields, requires exact UUID/ACTIVE, then offered active Doctor/slot facts, then invokes a separate transactional writer. Writer flush plus v2 outbox is atomic; doctor/hour partial unique index is the final duplicate guard.

Production Auth introspection and Patient HTTPS clients enforce strict payloads, TLS, bounds/deadlines, no redirect/retry/cache, no forwarded human token and no remote call within local write transaction. SERVICE original caller, unlinked/wrong self and ROLE_STAFF without delegated authority are denied. Patient credential outage/malformed response is503, not human401. Valid optional address/Unicode limits follow actual canonical Patient behavior.

BookingTcpIT uses real Appointment HTTPS/TCP and PostgreSQL, production Auth/Patient clients, but test-only Auth/Patient/Doctor-slot/lock doubles.32cases cover before-lock/no-write failures, expired/inactive/wrong issuer/audience, SERVICE distinction, missing linking, revocation, scope of self, Patient inactive/missing/malformed/oversized/deadline/credential failure, slot failure, UUID/snapshot input, concurrent one201+one409 and atomic outbox rollback. Tests inspect actual database rows/outbox and port call counts, not HTTP status alone.

B-02 status: PARTIAL / COMPONENT VERIFIED / TCP VERIFIED WITH TEST DOUBLES; REAL INTEGRATION NOT VERIFIED. Public booking defaults disabled. DoctorSlotPort has no authoritative production provider and returns503. Staff/delegated authority, real Auth, valid Appointment SERVICE bearer plus Patient current WorkScope grant, Redis operation and durable idempotency/replay remain activation gates. Do not enable shared booking based on a self-role token or these doubles. See [secure booking details](../../../appointment-service/docs/integration/APT_B02_SECURE_BOOKING.md).

Remote identity/ACTIVE/slot facts are snapshots; there is no cross-database atomicity or guarantee against revocation immediately after lookup. No automatic POST retry; lost response requires reconciliation until durable idempotency is implemented.

## Regression and evidence interpretation

Final expected suite accounting: Appointment104=45Surefire+59Failsafe (32booking TCP with doubles+27actual-binary provider tests); Patient normal254=180+74; Patient B-05 opt-in255=180+75 including one harness JUnit with8vectors. Vectors are not additional JUnit tests. Doctor59=26+33 is rerun separately without changing its source. Actual command result, timestamps, report hashes and F/E/S totals in the linked machine evidence take precedence.

Two failed attempts are retained: initial B-05 fixture had non-null relationship fields on non-relationship cases and strict preflight blocked it; corrected fixture only, no harness relaxation. A later cleanup attempt closed Spring context before Spring test listeners and caused after-class error; fixed by closing the owned datasource/resources while letting Spring close its own context. Neither failed attempt is labeled PASS. Final verification reruns all tests.

## Readiness and next actions

| Boundary / work | Status |
|---|---|
| UUID fresh-v2 / lifecycle | PROJECT_DECIDED, implemented and component verified |
| B-05 selected profile | CODE COMPLETE / COMPONENT VERIFIED / REAL APPOINTMENT PROVIDER VERIFIED / CONTRACT VERIFIED in this isolated environment |
| B-02 | PARTIAL / TCP WITH TEST DOUBLES / REAL NOT VERIFIED |
| B-04 | Historical real provider verification retained; Doctor regression rerun |
| Real Auth/scope / Gateway / Frontend | NOT VERIFIED |
| M6 / whole M5 / release | NOT EXECUTED / NOT DONE / NOT READY |

Before M5-E/F: provision actual Auth and scope authorities/service registry and tokens, adopt authoritative Doctor offered-slot/delegation contracts, implement/verify remaining Appointment idempotency/replay and operational locking/outbox gates, run real B-02 success/deny/outage/no-write, then Gateway/direct authorization and Frontend E2E. D-15 inventory/stable mapping/backup-restore/drain/backfill/restart/rollback/signoff are separate M6 work. No migration, shared deployment, legacy/shared DB write or Git history change occurred.

