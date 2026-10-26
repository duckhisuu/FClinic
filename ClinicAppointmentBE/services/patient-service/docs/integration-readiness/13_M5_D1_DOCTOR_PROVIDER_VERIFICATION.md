# M5-D1 — Actual Doctor provider / B-04 verification

24/10/2026, Asia/Saigon. **Doctor reference CODE COMPLETE / COMPONENT VERIFIED / REAL DOCTOR PROVIDER VERIFIED / B-04 CONTRACT VERIFIED** cho isolated binary/config/version dưới đây. **WHOLE PATIENT INTEGRATION NOT VERIFIED**; không M5/release/M6 DONE.

[Doctor implementation/config/reproduce](../../../doctor-service/docs/integration/DOC_REF_V1_IMPLEMENTATION.md), [machine evidence](../../evidence/m5-d1-doctor-provider-verification.json). Historical01–12 và M5-A/B/C/D evidence giữ nguyên; đây là kết quả mới thay thế blocker B-04 cho selected local profile, không sửa shared adoption.

## 1. Baseline / architecture

Branch main, HEAD238e1dd36cb759df444cf9441ef0012f0d12d2b5 giữ nguyên. Pre-edit Patient90entry manifest MATCH8be131f96288c9949480f76bb3a874c82a61e548b26c9327315742d25fd7e402; baseline254=180Surefire+74Failsafe,54TCP với doubles. Doctor trước task có0tests, full DTO/detail route, generic404, HTTP/ddl-update/default datasource và unconditional @PostConstruct seed. Không chạy legacy startup.

Selected PAT-REF-DOC-001 / patient-doctor-reference/1 schema SHA098d3651fe4bc5daad3ec813ce459acfc624a0888ad209f789aa6a4bcd08c172 **unchanged**. Shared B-04 SERVICE bearer proposal không tự trở thành adopted vì local Basic profile đã chạy thành công. Quyền kỹ thuật bao gồm Doctor+Patient được user ủy quyền; implementation này không giả provider support từ draft.

Provider implements strict dedicated Basic over HTTPS, exact component URL-form encoding, constant marker/closed wrapper/full11fields/explicit nulls, safe ID and correct active/inactive/missing. Credentials/grants thuộc server external registry, không headers/role/body. Secret comparisons fixed digest length across registry entries. Enabled missing/invalid config fails startup; disabled detail fails closed503. Reference-only profile closes directory/write/slot surfaces, không thêm alternate unprotected reference route.

Source business operations khác được giữ, unit regression filters/create/slot pass; sample writer chuyển explicit doctor-demo profile, incompatible with reference mode. Frontend api.js178/189/198 dùng legacy list/detail/slots không compatible reference-only instance; no Gateway/Frontend changes/deployment. Human directory authorization remains separate.

## 2. Actual runtime provenance

Final Doctor binary **07af740ea7f01beb037c5090bca9cdb547c3508f666c0ee39770f834e3b38d9b**, java -jar actual Boot4.1.1 / Java21.0.8 child process **PID35604**. Runtime started2026-10-23T19:57:40.669Z (02:57:40.669+07:00), owned localhost HTTPS random port, CA-signed localhost cert. No fake HTTP server/MockMvc/in-process Doctor replacement.

Dirty source manifest **29459d93251a70c22fcce101dafcc78d96de0be2f7f773b057592d99353e5fef**, includes Doctor pom/all src main+test/config/SQL. Digest=SHA256 UTF8(manifest-order path+space+fileSHA joinedLF/no trailingLF). Full list and observed JAR/process/config/cert hashes embedded in machine evidence. sourceCommit in original closed harness proof is **base HEAD**, not the commit of new code. Linked deploymentId record explicitly UNCOMMITTED with full dirty source manifest; actual Process object/start/JAR path/hash is recorded. No preflight assertion relaxed or fake deployment identity.

Native PostgreSQL16.10 fresh under Doctor target/doctor-real-b04-98190472017253372, random loopback port; owner provisions fresh schema and synthetic data before snapshot. Application role doctor_reference_app granted SELECT only/no schema ownership/write grants; **pg_stat_activity observed10connections using this actual role**, not merely configured username.

Fixtures:910001ACTIVE,910002INACTIVE,910003known absent,910004synthetic NaN fee for real technical500. Doctor count3/slot count0; full row snapshot hash unchanged before/after all requests. Role cannot INSERT/UPDATE/DELETE; no lookup writes/seed/schema update. Owner fixture provisioning is not provider lookup side effect. PG test cluster is loopback/native test infrastructure, not production credential/authentication verification for PostgreSQL.

Test CA/private PKCS12 generated only in owned ignored target, passwords via process env/keytool env option. Client verifies chain+hostname; real negative untrusted-root and IP-vs-DNS mismatch cases pass. HTTP plaintext cannot obtain data or redirect. Private PKCS12 deleted after run; Doctor process/PG stopped. Public cert hashes, safe config fingerprint and redacted process/source evidence retained. No credentials/private key/PHI stored in versioned docs/evidence.

## 3. Expected / actual B-04

Existing Patient RealProviderContractCheck ran against exact live JAR with production RemoteDoctorClientAdapter. No Patient decoder or public contract changes.

| Vector | Expected | Actual | Classification |
|---|---|---|---|
| ACTIVE |200/full11field/schema/marker/id, adapter active=true |200, exact profile, nullable fields present, active=true | REAL PROVIDER PASS |
| INACTIVE |200/active=false preserved |200/active=false via production adapter | REAL PROVIDER PASS |
| MISSING |404/DOCTOR_NOT_FOUND/data:null/[], marker; Optional.empty |404, selected error, empty adapter result | REAL PROVIDER PASS |
| INVALID_CREDENTIAL |Actual provider401/403, no data; consumer503 |401 UNAUTHENTICATED/no resource; production consumer503 | REAL PROVIDER PASS |
| WRONG_VERSION_REQUEST |400/406 UNSUPPORTED_REFERENCE_CONTRACT/no reflect/data |406, fixed implementation behavior, no Doctor data | REAL PROVIDER PASS |
| TECHNICAL_5XX optional |Controlled real provider5xx→consumer503 |Persisted invalid fee→500 INTERNAL_SERVER_ERROR/no secret; consumer503 | REAL PROVIDER PASS |
| Wrong resource/response marker/invalid payload fault injection |Controlled invalid responses rejected503 |Not injected; no production fault endpoint added | NOT EXECUTED |

Inactive/missing facts prove provider/adapter semantics; clinical Patient422/no-write on an actual whole chain was **not** executed. Prior component tests cover application422 mapping separately. No human Auth/scope provider participates in these read-only wire tests.

## 4. Actual builds/tests

Commands root:
~~~powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/doctor-service -am verify -B -ntp
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am -P real-provider-contracts verify -B -ntp
~~~

Third command executed as a child Maven from DoctorRealProviderIT with owned live provider config/environment, not after terminating provider. No secrets in CLI arguments. This Windows harness intentionally performs full Patient verification inside Doctor integration-test; root command requires Java/Maven/keytool and may take~90seconds.

| Suite/run | Actual |
|---|---|
| Doctor final verify | **BUILD SUCCESS/exit0,2026-10-24T02:58:49+07:00**,26Surefire+33Failsafe=59,F/E/S0 |
| Doctor unit breakdown | MachineCredentialsTest15, DoctorReferenceReaderTest9, DoctorLegacyBehaviourTest2 |
| DoctorRealProviderIT |33actual-binary integration cases including direct HTTPS auth/version/null/ID/no-write, TLS chain/hostname/plaintext, scope isolation and nested Patient harness |
| Patient real-provider profile | **BUILD SUCCESS/exit0,2026-10-24T02:58:45+07:00**,180Surefire+75Failsafe=255,F/E/S0; Check is1JUnit case containing6real vectors |
| Patient default verify |BUILD SUCCESS/exit0,2026-10-24T03:00:46+07:00;254baseline=180+74,F/E/S0 |
| Historical Patient54TCP |Rerun with upstream doubles, unchanged; not promoted to real Auth/scope/Appointment integration |

No new Patient tests/count beyond existing opt-in harness1case; production Patient/all254test source hashes unchanged. Patient pom only test-profile runner changed. No skip flags/disabled tests. Native PG fresh per suite, no shared DB.

Execution/debug ledger: first new IT compile failed OptionalAssert API use (fixed); first live run had all6vectors PASS but Maven failed on stale custom summary merged with historical preflight failure. Apache Failsafe3.5.6 [handleSummary](https://github.com/apache/maven-surefire/blob/surefire-3.5.6/maven-failsafe-plugin/src/main/java/org/apache/maven/plugin/failsafe/IntegrationTestMojo.java#L528) shares plugin-context merge flag across executions. Fix: one Failsafe execution includes all original IT patterns plus opt-in Check, reports/summary under target/real-provider-reports/current. All default cases remain included. Old failed evidence/summary was not deleted to make green.

A further setup review found embedded JDBC URL ?user=postgres could override the intended app username. Final run strips that query and asserts actual pg_stat_activity app connections; earlier runs are not used as SELECT-only-role proof. Final build/DB identity above is the accepted run. No test assertions/decoder/TLS/credential checks weakened.

## 5. Files / exclusions / next step

Doctor changed5existing files: pom, controller, DoctorResponse, GlobalExceptionHandler, DoctorApplicationService. Added reference package/config/filter/parser/reader/local errors/demo launcher, explicit TLS profile/fresh schema and5test source files. Exact paths/hashes in machine source manifest and Doctor implementation doc. Patient changed only pom + current status/handoff/readme/new13/evidence; reference/identity/scope/core/writer/tests/canonical unchanged.

Remaining: wrong-wire fault injection3vectors, load/restart/rotation/freshness operational tests, nonreference human directory auth, shared Basic-vs-bearer adoption, request correlation, D-21 OAS503 discrepancies. Basic revoke/rotation requires controlled config+restart; not hot reload. Stop listener or revoke client while retaining protected profile; never downgrade or deploy legacy binary as rollback on incompatible DB. No migration/cutover performed.

**M5-D2 exact tasks:** Appointment own UUID representation and stable persisted mapping dependency; resolve PENDING lifecycle without silent CONFIRMED conversion; implement protected /internal/v1/appointments/{id}/medical-reference with minimum4fields/selected statuses/version/errors/least privilege; implement B-02 trusted original human authorization + full Patient UUID/ACTIVE validation before lock/hold/write/history/outbox/idempotency success. Use separate task/build/DB, then real B-05/B-02 vectors. Real Auth/scope, Gateway/Frontend and M6 remain unverified/outside this task.

No Appointment/Auth/Gateway/Frontend/common-web/canonical modifications, existing/shared database mutation, private secret evidence, or Git history changes.

