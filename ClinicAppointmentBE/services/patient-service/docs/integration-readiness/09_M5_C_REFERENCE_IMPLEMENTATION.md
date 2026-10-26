# M5-C — Doctor & Appointment reference integration

24/10/2026, Asia/Saigon. **PAT-REF-DOC-001 / PAT-REF-APT-001: PROJECT_DECIDED. CODE COMPLETE / COMPONENT VERIFIED / TCP VERIFIED WITH STUB. PROVIDER NOT VERIFIED / INTEGRATION NOT VERIFIED.** Không M5 DONE hoặc RELEASE READY; không thực thi M5-D/E/F/M6.

## 1. Baseline và actual compatibility

Branch main; HEAD238e1dd36cb759df444cf9441ef0012f0d12d2b5, working tree chưa commit. Trước sửa: cả66source/config/test hashes của [M5-B](../../evidence/m5-b-verification.json) không drift; recomputed manifest **484386b057d90924ae63be7f920090db198005476e0b2aec9e3424c9b45db412**, đúng expected. Giữ tất cả178baseline tests và historical JSON.

| Boundary | Actual source | Quyết định consumer / provider gap |
|---|---|---|
| B-04 Doctor | [DoctorController](../../../doctor-service/src/main/java/com/fclinic/doctorservice/api/controller/DoctorController.java), [DoctorResponse](../../../doctor-service/src/main/java/com/fclinic/doctorservice/api/dto/DoctorResponse.java), [handler](../../../doctor-service/src/main/java/com/fclinic/doctorservice/api/exception/GlobalExceptionHandler.java): GET /api/v1/doctors/{id}, Long; full11fields, active boolean; detail getById không lọc inactive | Giữ route candidate/full data shape. Chưa có protected backend authorization/TLS/version marker trong source đã kiểm; cần provider/deployment bổ sung và verify |
| Doctor error | [common ApiResponse](../../../../common/common-web/src/main/java/com/fclinic/common/web/ApiResponse.java) chỉ status/message/data; actual404 không có domain code | Không chấp nhận legacy404 làm missing. Selected profile cần exact DOCTOR_NOT_FOUND + fieldErrors và version marker |
| B-05 Appointment | [controller](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/api/controller/AppointmentController.java), [DTO](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/api/dto/AppointmentResponse.java): public v1, Long patientId, full patient/contact/visit data | Không dùng legacy public endpoint. Chọn minimum protected SERVICE projection mới với Patient UUID; chưa có provider implementation |
| Appointment status | [enum thực tế](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/domain/model/AppointmentStatus.java): PENDING/CONFIRMED/COMPLETED/CANCELLED. Draft getAppointmentV2: CONFIRMED/CANCELLED/COMPLETED/NO_SHOW | Pin target draft vocabulary4values, không map PENDING một phía. Legacy PENDING là unknown protocol→503 |
| Runtime discovery | Doctor/Appointment application.yml register Eureka và configure HTTP ports; source không có service auth enforcement/SSL config được tìm thấy | Không suy Eureka registration thành trusted authority. Exact HTTPS URI external config; không chạy provider binaries/databases để thử |

Doctor draft version1.1-proposed-pack1 SHA b0128832bebd66296d11fad8e4fbce0f73b656f09e6d3827af88a724a19a25cd. Appointment draft1.1-proposed-pack1 SHA a333df2e726aba05922b6ba68d9d5924ee56cb3af1405315cc7f1d7adb3912dc. Đây là source pins, không owner adoption/runtime verification. Canonical pack ở docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack, không dùng pack root khác.

## 2. PAT-REF-DOC-001 — protected full Doctor profile

**PROJECT_DECIDED**, consumer contract patient-doctor-reference/1, [JSON Schema](contracts/patient-doctor-reference-v1.schema.json), ID urn:fclinic:patient:doctor-reference:1. SHA098d3651fe4bc5daad3ec813ce459acfc624a0888ad209f789aa6a4bcd08c172.

- Transport GET, candidate /api/v1/doctors/{id}; configured collection URI appends /{positiveSafeDecimalId}. Không URI/query/body từ user, không fallback legacy insecure transport.
- Dedicated Patient→Doctor machine Basic credential over HTTPS. Provider must authorize only needed detail-read capability; không human role/admin hoặc directory mutation/list grant. Exact provider permission identifier chưa được tuyên bố có sẵn.
- Request AND response bắt buộc X-FClinic-Reference-Contract: patient-doctor-reference/1. Response marker phải là provider constant cho implemented profile, không chỉ reflect arbitrary request header. Marker không thay backend authentication; real negative credential test vẫn bắt buộc.
- HTTP200 application/json, exact wrapper {status:200,message:string,data:Doctor}. Closed data đủ11fields: id,name,specialty,department,qualification,experienceYears,consultationFee,roomNumber,bio,avatarUrl,active.
- id positive safe Long<=9007199254740991, đúng requested ID. name/specialty/department string nonnull; qualification/roomNumber/bio/avatarUrl string hoặc null, vẫn required. experienceYears integer32 hoặc null theo actual DTO; consultationFee finite nonnegative number hoặc null; active required strict boolean, không missing/null/coercion/default false. String bound8192, full body bị bound riêng.
- Validate toàn wire trước khi tạo internal DoctorReference(id,active); bỏ directory fields sau validation. Không thay Patient public DTO hoặc domain.
- Legitimate missing: HTTP404 + correct version marker + exact {status:404,message:string,data:null,code:DOCTOR_NOT_FOUND,fieldErrors:[]}. Decoder trả Optional.empty; application maps422.
- 404 HTML/unmarked/legacy generic/wrong code/contradictory data/status→503, không giả missing.

Alternatives: không dùng unprotected plain actual runtime như “trusted reference”; không nhận sparse id/active trên full DTO profile; không tạo Doctor endpoint hoặc sửa common-web. Retain actual successful data shape để giảm provider change, thêm rõ security/version/error requirements phía provider. Compatibility body chỉ VERIFIED FROM SOURCE; full protected provider compatibility chưa verified.

## 3. PAT-REF-APT-001 — minimum SERVICE projection

**PROJECT_DECIDED**, consumer patient-appointment-medical-reference/1, [JSON Schema](contracts/patient-appointment-medical-reference-v1.schema.json), ID urn:fclinic:patient:appointment-medical-reference:1. SHAb2ce460335e273e227b45e982c6cefb2512d2625f6f7ff05f2342d29e34c1e86.

- GET candidate /internal/v1/appointments/{id}/medical-reference. Configured collection URI contains prefix through appointments; client appends /{safeId}/medical-reference. **Route mới là yêu cầu provider tương lai, chưa tự thêm vào Appointment Service.**
- Dedicated Patient→Appointment Basic credential/HTTPS, independent of Doctor/Auth/scope. Least privilege exact projection-read capability; không public full Appointment read, booking/cancel/reschedule/complete permission.
- Request/response marker X-FClinic-Reference-Contract: patient-appointment-medical-reference/1.
- Exact HTTP200 wrapper {status:200,message:string,data:{id,patientId,doctorId,status}}, all required/non-null, closed fields. Không patientName/phone/reason/schedule/fee hoặc full public DTO.
- id,doctorId positive safe Long. patientId lowercase UUIDv4, không Long fallback. Returned id phải đúng requested appointment. Well-formed other patient/doctor facts giữ nguyên cho PatientApplicationService so khớp target→422; không sửa response để khớp input.
- Status giữ enum đầy đủ đến cuối decoder: **CONFIRMED,CANCELLED,COMPLETED,NO_SHOW**. Chỉ sau validate mới convert internal cancelled boolean. CANCELLED→application422; valid other3statuses không tự loại chỉ vì chưa COMPLETED. Missing/null/unknown/lowercase/PENDING→503.
- WorkScope validVisit là kiểm tra khác; status không tạo encounter/clinical assignment. NO_SHOW reference được decoder chấp nhận theo non-CANCELLED predicate không có nghĩa valid visit thật; authority vẫn phải authorize và D-17 không đổi.
- Exact selected HTTP404 uses code APPOINTMENT_NOT_FOUND/data:null/fieldErrors:[] + correct marker→Optional.empty→422. All other ambiguous/technical responses→503.
- Không auto apply Appointment date hoặc mutate state. Projection không gồm entity version/timestamp; contract version marker là wire version, không Appointment optimistic version.

Không chọn public draft getAppointmentV2/full response vì excess data và chưa có runtime support. Provider phải triển khai UUID projection, backend authentication, marker/errors và target status vocabulary; Patient không tự backfill/migrate hoặc map legacy Long/PENDING.

## 4. Production code và configuration

| File mới | Vai trò |
|---|---|
| [ReferenceSettings](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/ReferenceSettings.java) | Typed independent endpoint settings, validated bounds, redacted toString |
| [ReferenceHttpClient](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/ReferenceHttpClient.java) | HTTPS GET only, selected profile/path/version, separate credential/client/capacity, bounded streaming/full deadline, transaction guard |
| [ReferenceJson](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/ReferenceJson.java) | Strict parser/wrapper/error/ID primitives, duplicate/trailing/unknown/type validation |
| [DoctorReferenceDecoder](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/DoctorReferenceDecoder.java) | Full Doctor wire before internal facts |
| [AppointmentReferenceDecoder](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/AppointmentReferenceDecoder.java) | Minimum projection, UUID/safe IDs and enum before boolean |
| [RemoteDoctorClientAdapter](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/RemoteDoctorClientAdapter.java) | DoctorClientPort production bean |
| [RemoteAppointmentClientAdapter](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/RemoteAppointmentClientAdapter.java) | AppointmentClientPort production bean |
| [PatientReferenceConfiguration](../../src/main/java/com/fclinic/patientservice/config/PatientReferenceConfiguration.java) | Exactly-one selection, credential isolation, client lifecycle |
| [doctor-reference profile](../../src/main/resources/application-doctor-reference.yml), [appointment-reference profile](../../src/main/resources/application-appointment-reference.yml) | Separate explicit opt-ins |

Only existing production source changed: [PatientConfiguration](../../src/main/java/com/fclinic/patientservice/config/PatientConfiguration.java), thêm conditional false/default cho hai existing503fallbacks. **65/66baseline source/config/test files unchanged**, including all178baseline tests, Identity/WorkScope production, PatientPolicy/Controller/ApplicationService/Writer/persistence/schema/POM. Internal port signatures/records không đổi.

Để enable cả chain, chỉ với compatible provider và prepared isolated v2 DB:
~~~powershell
java -jar ClinicAppointmentBE/services/patient-service/target/patient-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=identity-introspection,scope-authority,doctor-reference,appointment-reference
~~~

Cần DB/Auth/scope settings từ README/07/08 và:
- PATIENT_DOCTOR_REFERENCE_URI, PATIENT_DOCTOR_REFERENCE_CLIENT_ID, PATIENT_DOCTOR_REFERENCE_CLIENT_SECRET.
- PATIENT_APPOINTMENT_REFERENCE_URI, PATIENT_APPOINTMENT_REFERENCE_CLIENT_ID, PATIENT_APPOINTMENT_REFERENCE_CLIENT_SECRET.
- PATIENT_REFERENCE_CONNECT_TIMEOUT default1s; PATIENT_REFERENCE_EXCHANGE_TIMEOUT default3s.
- Protected external per-endpoint patient.reference.doctor.* / appointment.*: trust-store PKCS12 + trust-store-password optional; max-response-bytes default65536; max-concurrent-requests16.

URI phải HTTPS, host/nonempty collection path, không credentials/query/fragment/trailing slash/dot normalization. ID chỉ decimal digits từ validated long, không URI injection. TLS1.2/1.3 chain + hostname verification; default JVM trust hoặc explicit CA truststore, không trust-all. No endpoint/secret default, discovery fallback, human bearer forwarding hoặc production doubles. Basic credentials URL-form encoded từng component trước Base64 như M5-A/B.

Config rejects reuse client ID OR secret giữa Doctor/Appointment và introspection/admin/clinical settings được cấu hình. Enabled thiếu/sai config→startup fail closed; disabled/default→existing503fallback. Bean selection exactly-one được test riêng/both. Secret qua deployment store/env, không commit values. Rotation qua protected config + restart; actual provider overlap/revoke chưa verify.

PROJECT_DECIDED per-endpoint bounds: connect/exchange100ms..30s và connect<=exchange; response1KiB..256KiB; concurrency1..256. Capacity đầy→503, full exchange deadline including body/cancel. No application retries, no redirects (actual302 Location tested), no reference/activation cache; no-store header. Không là shared SLO/whole-chain budget. No token/credential/provider body/context logging; error không có raw cause/URL/clinical details. HTTP wire/APM/access logs cần redact trong deployment. Correlation/requestId F-05 còn ngoài M5-C, không claim đã verified.

## 5. Reference/error/transaction semantics

| Provider outcome | Internal/application/Patient behavior |
|---|---|
| Doctor strict200 active=true | Continue scope-authorized create |
| Doctor strict200 active=false / selected missing404 | active=false / Optional.empty →422 REFERENCE_MISMATCH |
| Appointment selected missing404 / CANCELLED / well-formed wrong patient or doctor | Optional.empty/cancelled/mismatched facts→422 |
| Wrong returned resource ID, invalid/missing fields/type/UUID/enum, duplicate/trailing/truncated/oversized/wrong wrapper/version/content | typed503 DEPENDENCY_UNAVAILABLE |
| Machine401/403,429,5xx,unexpected status/redirect,TLS/network/deadline/capacity | typed503, never incoming human401/403 or business missing |
| Invalid internal lookup id<=0 or >safe max |503 before network; HTTP Patient request validation still400 as before |
| Local transaction active at lookup |503 before any network; không mở distributed transaction |

Clinical order giữ nguyên: M5-A identity → M5-B clinical scope + validVisit → existing parent lookup → Doctor reference → Appointment reference only if linked → separate PatientWriter proxy transaction → lock/recheck ACTIVE → insert/flush/version/revision atomic commit. Remote client rejects *any* active local transaction, stricter than only write; không thêm network call vào writer. PUT giữ scope/author/version/reason/no-op semantics, **không revalidate immutable remote references**.

Walk-in bỏ only Appointment, vẫn Doctor/scope/visit/ACTIVE/audit. Linked non-null UNIQUE appointment guard cuối cho race; lose409 APPOINTMENT_RECORD_EXISTS, rollback loser record/revision, không orphan. Không generate fake appointment/visit, complete/cancel/book/reschedule request.

Remote status/activation/assignment có thể đổi sau snapshot cuối; không hứa atomic consistency liên DB hoặc prevent mọi revoke sau check. Không thay D-17 exceptional correction workflow, không blind retry Patient mutations khi lost response.

## 6. Checkpoint và actual verification

Command root cho cả checkpoints:
~~~powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp
~~~

| Checkpoint | Actual result |
|---|---|
| C1 Doctor | BUILD SUCCESS207tests =178baseline+29Doctor/config tests; finished2026-10-24T01:44:19+07:00 |
| C2 Appointment | BUILD SUCCESS226tests; C1/baseline preserved +19Appointment/config cases; finished2026-10-24T01:46:59+07:00 |
| C3/C4 full Patient TCP + final full verify | **BUILD SUCCESS/exit0,249tests =175Surefire+74Failsafe, failures0/errors0/skipped0; finished2026-10-24T01:52:26+07:00**, Maven total reported01:08min |

Không focused-only final, không skip/disable tests hoặc change stack. Compiler deprecation/Mockito dynamic-agent warnings còn; không coi warnings là skipped failures. M5-C runs trên đây đều pass, không có failed run cần che. [Evidence JSON mới](../../evidence/m5-c-verification.json) pin all source/test/contracts/reports/JAR; digest nguồn1924f0e22fd5de78a9b288d07cd26df262b75bbf8b8ad24def84f534cf96eb17. Source digest algorithm sorted relative path + space + SHA joined LF/no trailingLF UTF8. Historical JSON không ghi đè; target reports được tạo lại bởi Maven.

| Suite mới | Tests | Verified layer |
|---|---:|---|
| [DoctorReferenceDecoderTest](../../src/test/java/com/fclinic/patientservice/reference/DoctorReferenceDecoderTest.java) |16| Active/inactive/missing; all11required; null/types/safe IDs/wrong resource; strict404/wrapper/unknown/duplicate/truncated/trailing |
| [DoctorReferenceTransportTest](../../src/test/java/com/fclinic/patientservice/reference/DoctorReferenceTransportTest.java) |8| Real HTTPS test provider: GET safe ID/exact URI/version/machine auth, current relookup,404/error/Location redirect/no retry, response/version/content/body bound, transaction guard,TLS/hostname, latch deadline/capacity |
| [AppointmentReferenceDecoderTest](../../src/test/java/com/fclinic/patientservice/reference/AppointmentReferenceDecoderTest.java) |13| Status4values; required/null/UUID/safe IDs, preserve well-formed mismatch facts, wrongID503,404/publicFull/unknown/duplicate/malformed |
| [AppointmentReferenceTransportTest](../../src/test/java/com/fclinic/patientservice/reference/AppointmentReferenceTransportTest.java) |4| Actual HTTPS minimum path/credential, no cache/cancel default,404vs503/error/version/transaction guard |
| [ReferenceConfigurationTest](../../src/test/java/com/fclinic/patientservice/reference/ReferenceConfigurationTest.java) |7| Default closed, exactly-one Doctor/both beans, invalid/insecure settings, isolation from other reference/Auth/scope, redacted config |
| [PatientReferenceTcpIT](../../src/test/java/com/fclinic/patientservice/reference/PatientReferenceTcpIT.java) |23| Actual Patient RANDOM_PORT HTTP + all four production ports; five independent HTTPS doubles Auth/admin/clinical/Doctor/Appointment; fresh PG16.10; linked/noncancel statuses/walk-in,422vs503/scope/bodyspoof/no-write, INACTIVE, controlled duplicate race, atomic revision rollback500, immutable-reference PUT/no-op |

New helpers [ReferenceFixtures](../../src/test/java/com/fclinic/patientservice/reference/ReferenceFixtures.java), [TestReferenceServer](../../src/test/java/com/fclinic/patientservice/reference/TestReferenceServer.java) là test-only. C3 không MockitoBean bất kỳ outbound port nào; revision adapter spy chỉ dùng inject local failure cho rollback test. Provider calls thực qua HTTPS dùng production clients; provider dữ liệu/authorization vẫn doubles, không real provider integration. JAR không có test fixtures/classes/keys.

All178baseline test sources/hashes không đổi; thêm71cases (48Surefire,23TCP). Total actual direct TCP suites54cases =12M5-A+19M5-B+23M5-C; PatientServiceIT20 vẫn MockMvc/PG, không gọi là TCP. Bốn native PG16.10 clusters fresh dưới target/patient-v2-it-*, patient-identity-tcp-it-*, patient-scope-tcp-it-*, patient-reference-tcp-it-*, loopback random ports, approved empty SQL, đã stop sau test. Clock synthetic registry2026-10-20T03:00:00Z; business DOB timezoneAsia/Saigon. Không dùng shared DB/H2/migration hoặc start provider legacy binaries.

## 7. Traceability và phần chưa verified

Mapping tới05 matrix; “PASS stub/component” không thay trạng thái real-provider rows trong audit05:

| Matrix ID | Evidence M5-C | Real provider status |
|---|---|---|
| T-REF-01 | Doctor decoder/HTTPS + TCP doctorBusinessFailures/linked/walkIn: active continue, inactive/missing422 | NOT EXECUTED |
| T-REF-02 | Doctor transport status/credential/TLS/timeout/body/ID tests + TCP technical/invalidDoctor:503/no-write | NOT EXECUTED; shared SLO chưa measured |
| T-REF-03/04 | Appointment decoder/HTTPS + TCP selected missing/cancelled/wrongPatient/wrongDoctor422; valid statuses | NOT EXECUTED |
| T-REF-05 | Unknown/PENDING/null/malformed/schema/marker/resource ID→503; no default cancelled=false | NOT EXECUTED |
| T-REF-06 | Both transport transaction guards, all4port TCP success/order events, walk-in skip, ACTIVE writer guard; source writer unchanged | PASS stub subset; actual provider/correlation/requestId missing |
| T-CL-01/02 | Actual TCP linked3statuses and two walk-ins with verified test identity/scope, Doctor/APT GET only | PASS stub, not real clinical authority |
| T-MED-01 | Actual TCP two concurrent linked creates synchronized by provider CyclicBarrier:201+409,1record/1revision,no orphan | PASS PG/TCP with providers doubles |
| T-MED-02/03 | Existing178baseline concurrency/version/scope tests unchanged; new inactiveGuard/PUT no-op without ref relookup | Partial target variants; not all real-provider interleavings |
| T-MED-04 | Actual HTTP local revision fault after refs→generic500; record/revision rollback, counts/versions unchanged | PASS authorized test injection; real dependencies NOT EXECUTED |

No100%coverage/full DoD/full JSON Schema compliance claim. Schema profile is pinned alongside decoder vectors; no independent whole-schema validator or real provider contract suite added in M5-C.

## 8. Provider handoff cho M5-D, không thực thi M5-D

- **Doctor:** expose compatible protected candidate GET/full shape with explicit required nulls/safe IDs; implement fixed version header and selected domain404, TLS chain/hostname and dedicated read-only machine client. Provide build/source/schema hashes, actual positive/invalid credential/notfound/inactive/malformed vectors. Do not just reflect version marker or ignore credentials.
- **Appointment:** implement new protected minimum UUID projection (not full public DTO), exact version/errors/status vocabulary and least privilege client; evidence for legacy PENDING/Long incompatibility migration belongs own service. No Patient backfill of Appointment DB.
- **Auth/scope/environment:** actual issuer/mapping/activation/assignment/valid encounter sources, credentials separately provisioned/rotated, protected exact URIs/CA trust, synchronized clocks and chain budget/correlation rules; secrets delivered via deployment store, not evidence docs.
- **M5-D readiness:** consumer code/schema/stub vectors are ready to run against compatible provider builds; actual provider/consumer contract tests/adoption evidence remain NOT EXECUTED. Negative credentials must deny on provider; success from a stub or marker alone proves no provider authorization enforcement.
- **M5-E/F:** Gateway/B-02 consumer UUID and original-human authorization, frontend/error/conflict flows and E2E/signoff remain separate tasks; not changed here. F-05 request correlation and D-21 OAS503 coverage remain open.
- **M6:** no migration/cutover/clone/legacy writer action. No provider/shared/common-web source changed, no Git reset/clean/checkout/commit/push; HEAD preserved.

Rollback reference activation: disable relevant profile/endpoint.enabled and restart→existing503fallback, not allow-all or insecure/legacy fallback. Do not run v1 Patient binary on v2 schema. No deployment performed.

