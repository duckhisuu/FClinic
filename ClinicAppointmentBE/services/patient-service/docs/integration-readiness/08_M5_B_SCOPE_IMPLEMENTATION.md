# M5-B — administrative & clinical scope integration

Ngày24/10/2026, Asia/Saigon. **PROJECT_DECIDED / CODE COMPLETE / COMPONENT VERIFIED / TCP VERIFIED WITH STUB / PROVIDER NOT VERIFIED.** Không INTEGRATION VERIFIED, không M5 DONE. Người dùng đã ủy quyền quyết định kỹ thuật Patient; không đợi điều khoản approval cũ của audit01–06. Không thay approved pack/shared decision status.

## 1. Discovery và quyết định PAT-SCOPE-001

Baseline main/HEAD238e1dd36cb759df444cf9441ef0012f0d12d2b5, working tree có thay đổi từ trước; cả52source/config/test hashes của M5-A match trước sửa. Không tìm thấy Auth runtime, scope/assignment/encounter endpoint hoặc authoritative scope store trong Doctor/Appointment source đã quét. Draft endpoint không được coi là provider runtime.

Chọn **hai logical HTTPS authority boundaries**, có thể được triển khai cùng host hoặc khác host, credential và capacity tách riêng:
- **Administrative Assignment Authority** là source of truth cho current receptionist assignment, operation-specific admin permission, patient-object grant và authorized search membership.
- **Clinical Assignment/Encounter Authority** là source of truth cho trusted doctor↔subject mapping/assignment, patient relationship, operation-specific clinical permission và valid encounter cho linked/walk-in.
- Nguồn record thực tế và provider runtime **chưa tồn tại/chưa được xác minh** trong repository. Đây là consumer contract PROJECT_DECIDED, không khẳng định Auth/Doctor/Appointment đã có dữ liệu hoặc endpoint này. Provider phải resolve assignment/encounter từ nguồn của mình; request caller fields chỉ là lookup context do authenticated Patient assertion, không là grant.
- Không tạo grant table trong Patient DB. Không dùng role, same-clinic, phone/email, browser headers, appointmentId null hoặc body doctorId làm authority. Không dùng Appointment reference như scope evidence, không dùng scope evidence như Appointment reference.

Không chọn local role→grant registry vì không có relationship/revocation evidence; không query database của service khác. Không reuse introspection endpoint/credential cho scope. Không mở dev allow-all.

## 2. Port và operation-specific policy

[WorkScopePort](../../src/main/java/com/fclinic/patientservice/application/port/out/WorkScopePort.java) giữ sáu methods cũ, thêm overload mang AdministrativeAction, ProfileAction, ClinicalAction, SearchAction. Default overload delegate method cũ để giữ compatibility của existing implementations; production adapter override tất cả overload và không suy operation từ HTTP headers/URI/thread-local. Legacy production methods chọn CREATE hoặc READ/search theo role, không được core dùng để thay quyền write.

[PatientPolicy](../../src/main/java/com/fclinic/patientservice/application/security/PatientPolicy.java) vẫn giữ USER/self/staff/doctor/SERVICE và author boundaries; chỉ bổ sung operation rõ ràng và typed dependency guard. [Controller](../../src/main/java/com/fclinic/patientservice/api/controller/PatientController.java) POST medical gọi prepareCreateMedical (CREATE), không gọi UPDATE precheck. Không yêu cầu quyền sửa record để tạo record; trước M5-B chỉ có clinical boolean chung. PUT vẫn UPDATE, local writer kiểm cả immutable author subject và doctorId như cũ.

| Use case | Scope request / authority |
|---|---|
| Profile POST | ADMIN_ASSIGNMENT_CREATE / administrative |
| Staff profile GET | ADMIN_ASSIGNMENT_READ + ADMIN_PROFILE_READ(patient UUID) |
| Profile PUT, kể cả no-op | ADMIN_ASSIGNMENT_UPDATE + ADMIN_PROFILE_UPDATE(patient UUID) |
| Staff search | ADMIN_ASSIGNMENT_SEARCH + ADMIN_SEARCH → authorized UUID set |
| Doctor profile GET / search | DOCTOR_PROFILE_READ(patient UUID) / DOCTOR_SEARCH, clinical |
| Medical list/detail | MEDICAL_READ(patient UUID), clinical; PATIENT self vẫn direct self permission |
| Medical POST | MEDICAL_CREATE + VALID_VISIT(patient, trusted doctor, nullable appointment, visitDate), clinical |
| Medical PUT/no-op | MEDICAL_UPDATE(patient UUID), clinical; writer kiểm author/version/reason |
| Appointment SERVICE profile detail | APPOINTMENT_PROFILE_READ(patient UUID), administrative, thêm serviceName và SVC_PATIENT_READ ceiling từ M5-A |

Staff/doctor gates không tạo grant: role đúng vẫn phải gọi authority. Doctor thiếu trusted doctorId bị deny. ADMIN/staff không clinical override. Với dual-role, profile branch mang explicit ADMIN_READ hoặc DOCTOR_READ; search ưu tiên administrative nếu receptionist assignment đã được grant, nếu không dùng clinical doctor path. Không union UUIDs hoặc fallback khi provider technical failure.

Search deny→null→403; authorized empty set→200/content[]/total0; bounded set chỉ lọc/count/sort trên authorized UUIDs. Không wildcard, không truncate oversized scope, không dùng empty/null để che outage. AND filters/paging/order giữ nguyên repository.

## 3. Versioned wire contract và provenance

[Schema patient-work-scope/1](contracts/patient-work-scope-v1.schema.json), ID urn:fclinic:patient:work-scope:1. $defs/request mô tả outbound request; root là response. Hash pin trong [evidence](../../evidence/m5-b-verification.json). Không sửa canonical Patient/Auth OAS.

POST tới exact URI deployment cấu hình, HTTPS TLS1.2/1.3 và certificate/hostname validation; application/json/Accept JSON, Cache-Control no-store. Authorization Basic dùng **scope client ID/secret riêng**, URL-form encode từng thành phần trước Base64; không forward original bearer/browser headers. Credentials authority riêng nhau và khác introspection; config từ chối reuse client ID hoặc secret. Provider phải authorize client cho đúng logical authority, exact operations và Patient caller-assertion delegation.

Closed request:
- contractVersion, random UUIDv4 nonce, requestedAt UTC, authority ADMINISTRATIVE/CLINICAL, exact operation.
- caller={subject,principalType,patientId,doctorId,serviceName}, tất cả từ M5-A verified context/registry, không từ request headers/body. Không gửi token, phone/email/full profile/clinical text. Caller subject là opaque, không split và không coi userId=doctorId=patientId.
- patientId object UUID hoặc null cho assignment/search; appointmentId/visitDate chỉ dùng VALID_VISIT. Full canonical UTC strings và nullable fields đều explicit.
- Scope provider phải pin identity issuer/subject namespace được phép cho Patient backend client, xác nhận trusted subject↔doctor mapping/assignment từ nguồn của mình hoặc explicit trusted identity delegation. Đổi issuer/mapping realm cần reprovision và integration tests; wire không phải JWT claims hoặc signed reusable attestation.

Closed response: contractVersion, request **echo toàn bộ exact request**, evaluatedAt, allowed:boolean, patientIds, evidence. Duplicate keys/trailing JSON/unknown/missing/type sai bị reject. Nonce+full structural equality bind caller/type/doctor/mapped patient/serviceName, operation, target UUID, linked/walk-in và visitDate; không chấp nhận response của principal/resource/operation khác hoặc replay decision trước.
- evaluatedAt UTC phải >=requestedAt−1s và <=current Clock+1s. Đây là local clock-tolerance decision, không shared standard; deployment cần synchronized clocks.
- allowed=false yêu cầu evidence:null,patientIds:null → business denial.
- allowed=true cần evidence={assignmentId,active,validFrom,validUntil,visitId}. Assignment opaque, interval hợp lệ; active=false/not-yet-current/expired tại Clock→business denial403. Malformed/impossible interval hoặc missing/type sai→503.
- VALID_VISIT bắt buộc visitId nonblank từ authority; các operation khác visitId=null. Provider phải kiểm encounter thực sự thuộc Doctor/Patient/nullable linked appointment và visitDate request; echo không thay thế lookup dữ liệu trusted. Walk-in vẫn cần encounter evidence, không fake visit runtime.
- Search grant có unique lowercase UUIDv4 array, có thể rỗng, <=maxSearchPatients; nonsearch patientIds=null. Exceed limit→503; không grant-all/truncate.
- Decoder kiểm additional semantic/Clock/control-character/binding constraints ngoài schema shape. Tests không được coi là full JSON Schema conformance hoặc actual provider contract pass.

Mỗi lookup gọi provider mới, nonce mới, không positive/negative grant cache. Controller/application hiện recheck trước writer; revocation giữa hai check được test deny/no-write. Không hứa atomic distributed revocation sau decision cuối cùng hoặc trong local commit. Không mở network call trong local write transaction để che TOCTOU.

## 4. Production implementation và F-06

| File mới | Vai trò |
|---|---|
| [ScopeSettings](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/scope/ScopeSettings.java) | Immutable typed settings/limits; startup validation; secret-redacted toString |
| [ScopeQuery](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/scope/ScopeQuery.java) | Explicit wire DTO; UTC strings, exact nullable fields, nonce |
| [ScopeDecisionDecoder](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/scope/ScopeDecisionDecoder.java) | Strict decode, binding/freshness/evidence/search checks |
| [ScopeAuthorityClient](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/scope/ScopeAuthorityClient.java) | Isolated HTTPS client/credential/semaphore, bounded streaming response, deadline/cancel, no redirects/application retries |
| [RemoteWorkScopeAdapter](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/scope/RemoteWorkScopeAdapter.java) | WorkScopePort production implementation, logical authority routing, role/mapping prerequisite + remote grant; reject lookup while local transaction active |
| [PatientScopeConfiguration](../../src/main/java/com/fclinic/patientservice/config/PatientScopeConfiguration.java) | Exactly-one real port selection and credential separation/lifecycle |
| [Profile](../../src/main/resources/application-scope-authority.yml) | Explicit opt-in, no endpoint/credential defaults |

Files cũ đổi: WorkScopePort, PatientPolicy, PatientController, PatientConfiguration (fallback condition). Không đổi PatientWriter/ApplicationService/domain/entities/repository/schema/POM/identity production implementation/GlobalExceptionHandler.

**F-06 resolved:** client/decoder đưa TLS/network/timeout/status/credential/invalid payload thành PatientError(DEPENDENCY_UNAVAILABLE), không false/null. Policy.scope chỉ bao các scope port calls, giữ typed403/503, chuyển unexpected RuntimeException hoặc bất hợp lệ ở dependency sang503 không kèm private cause. Không biến mọi application/DB/domain error thành503; existing exception handler vẫn canonical envelope và500 cho lỗi ngoài scope.

| Tình huống | Outcome |
|---|---|
| Verified identity nhưng thiếu grant/relationship/current assignment/valid visit | 403 FORBIDDEN |
| Authorized empty search set | 200, empty content, total0 |
| Missing/malformed/expired/inactive user auth | M5-A401, trước scope |
| Scope HTTP401/403 (backend credential/permission),5xx,429,redirect,network/TLS/deadline/capacity | 503 DEPENDENCY_UNAVAILABLE |
| Misbound/malformed/oversized/stale-evaluation response | 503, không grant và không write |
| Unconfigured/disabled scope | Existing deny fallback: false/null→403; không pretend đã gọi provider |
| enabled nhưng settings thiếu/sai | Startup fail closed, không chọn fallback |
| Linked scope valid nhưng Doctor/Appointment reference unavailable | 503 từ M5-C reference boundary; không có medical write |

Generic status/message/data:null/code/fieldErrors giữ nguyên. Không log token/credential/caller payload/clinical/provider response/stack vào API. Không expose provider URL/cause. Auth và scope credentials không có trong evidence; test fixture credentials ngẫu nhiên/test-only. Outgoing HTTP wire/APM logs phải được operator redact; F-05 correlation chain/telemetry chưa implement ở M5-B.

## 5. Configuration và rollout

Chỉ bật sau khi provider compatible/local profile và isolated target v2 DB đã provision. Không chạy command này với datasource legacy/shared:

~~~powershell
java -jar ClinicAppointmentBE/services/patient-service/target/patient-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=identity-introspection,scope-authority
~~~

Cần DB v2 env và M5-A identity settings như README/07. Scope env:
- PATIENT_ADMIN_SCOPE_URI, PATIENT_ADMIN_SCOPE_CLIENT_ID, PATIENT_ADMIN_SCOPE_CLIENT_SECRET.
- PATIENT_CLINICAL_SCOPE_URI, PATIENT_CLINICAL_SCOPE_CLIENT_ID, PATIENT_CLINICAL_SCOPE_CLIENT_SECRET.
- PATIENT_SCOPE_CONNECT_TIMEOUT mặc định1s, PATIENT_SCOPE_EXCHANGE_TIMEOUT mặc định3s.
- Protected external config patient.scope.administrative.* và clinical.*: optional trust-store (PKCS12), trust-store-password; max-response-bytes mặc định262144; max-concurrent-requests32; max-search-patients1000.

Mỗi authority có own HttpClient/semaphore/credential/limits; endpoint có thể cùng host nhưng không chia sẻ credential. JVM CA trust mặc định hoặc custom CA store, hostname validation vẫn bật. Không default password/token/URL, không profile permissive. No automatic credential fallback; rotation qua protected deployment config + rolling restart, provider overlap/revoke cần verify thật.

Bounds: connect/exchange100ms..30s, connect<=exchange; response1KiB..1MiB; concurrency1..256; search1..10000. Operator phải cấu hình response limit đủ chứa max search scope. Full-read deadline + capacity limit không là SLO hoặc tổng budget mọi query trong operation: profile PUT có nhiều rechecks; chain budget/Gateway/FE timeout vẫn phải phối hợp D-21. Không blind retry Patient POST/PUT khi lost response; scope POST chỉ là authorization evaluation, client vẫn không application retry.

## 6. Tests/evidence

Actual command từ root:
~~~powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp
~~~

**BUILD SUCCESS/exit0, finished2026-10-24T01:22:10+07:00,57.566s. 178tests =127Surefire +51Failsafe; failures0/errors0/skipped0.** Baseline114 giữ nguyên cases/assertions, thêm64. [Machine-readable evidence](../../evidence/m5-b-verification.json) pin source/test/contract/report/build hashes. Không ghi đè historical verification-summary.json hoặc m5-a-verification.json; target reports được Maven tạo lại, hashes pin lần này.

| Suite mới | Count | Evidence thực tế |
|---|---:|---|
| [ScopeDecisionDecoderTest](../../src/test/java/com/fclinic/patientservice/scope/ScopeDecisionDecoderTest.java) |27| All operation grants, deny/empty/bounded search, exact caller/resource/nonce/visit binding, revoked/future/expired evidence, linked/walk-in, malformed/type/unknown/duplicate/trailing/replay/limits |
| [ScopePolicyTest](../../src/test/java/com/fclinic/patientservice/scope/ScopePolicyTest.java) |4| Admin actions tách biệt, clinical CREATE≠READ≠UPDATE, null/empty search, F-06 unknown runtime→503/business denial403 |
| [ScopeConfigurationTest](../../src/test/java/com/fclinic/patientservice/scope/ScopeConfigurationTest.java) |5| Default deny, exactly-one production bean, incomplete/insecure settings reject, separated credentials, redacted settings |
| [ScopeAdapterTest](../../src/test/java/com/fclinic/patientservice/scope/ScopeAdapterTest.java) |9| Actual HTTPS client→test authority: credentials/authority routing/current recheck, scope sets, linked/walk-in, HTTP/invalid/oversize/TLS/hostname failures, latch deadline/capacity, transaction guard |
| [PatientScopeTcpIT](../../src/test/java/com/fclinic/patientservice/scope/PatientScopeTcpIT.java) |19| Actual random-port Spring servlet, production IdentityVerifier+WorkScopePort, Auth+scope HTTPS doubles, fresh PG16.10; visibility/count/AND filters/no-write/version/revision, forge/misbound/expired/denial/technical503, role/self/author/nested, revoke before no-op/mutation and between rechecks, timeout latch, linked reference unavailable/duplicate guard |

[ScopeFixtures](../../src/test/java/com/fclinic/patientservice/scope/ScopeFixtures.java) và [TestScopeServer](../../src/test/java/com/fclinic/patientservice/scope/TestScopeServer.java) chỉ test source, dùng M5-A generated TLS fixtures, không package vào runtime. Auth provider, administrative/clinical authority và Doctor/Appointment facts trong tests là doubles. Scope bean là production spy với real behavior; một test fault-inject untyped exception để kiểm F-06, không mock quyền cho suite mới.

Ba PG suites tạo3fresh native PostgreSQL16.10 clusters target/patient-v2-it-*, patient-identity-tcp-it-*, patient-scope-tcp-it-*, loopback/random port, apply approved empty SQL; đã stop sau test. Không DB môi trường cũ, không H2, không migration. New TCP suite có19tests, M5-A12TCP còn pass →31TCP tests với doubles. Không coi MockMvc của PatientServiceIT là TCP.

Bốn baseline fixtures đổi đúng một mock-default line sang CALLS_REAL_METHODS để new port compatibility defaults delegate existing stubs: PatientPolicyTest, PatientOrchestrationTest, PatientServiceIT, identity/PatientIdentityTcpIT. Đã đảo riêng dòng này trong memory và SHA-match original M5-A file hashes cả4: không sửa assertions/test bodies hoặc skip testcase. Tất cả114baseline pass. Domain/transaction/concurrency/atomic revision tests cũ vẫn chạy.

Lịch sử lượt này: initial82baseline Surefire pass01:12:48; thêm unit/HTTPS tests127pass01:16:18; first full178 có1error ở linked-reference test restubbing Mockito, sửa doReturn không thay production behavior; full rerun178pass01:22:10. Existing Mockito dynamic-agent/deprecation warnings còn, không tắt tests để che.

## 7. Verification levels và M5-C

| Level | Trạng thái |
|---|---|
| PROJECT_DECIDED | PAT-SCOPE-001 + local v1 contract/budgets/trust/delegation model |
| CODE COMPLETE | Production WorkScopePort/clients/strict decoder/config và F-06 |
| COMPONENT VERIFIED | Unit/policy/config/HTTPS và PG assertions có thật |
| TCP VERIFIED WITH STUB | Actual servlet HTTP + both production adapters + Auth/scope HTTPS doubles + fresh PG |
| PROVIDER VERIFIED | NOT EXECUTED; authority/Auth runtime, source-of-truth data và provisioning chưa có evidence |
| INTEGRATION VERIFIED | NOT EXECUTED; actual issuer/namespace/delegation/currentness/Gateway/E2E chưa verify |
| M5/M6 | Chưa DONE; M6 không execution |

Provider handoff: triển khai exact query/response schema, least-privilege client permissions và namespace binding; current assignment/revocation + authoritative patient membership/doctor relationship/encounter data; semantic deny200 vs technical error; build/hash/endpoint/TLS/client provisioning/rotation evidence. Chạy cùng valid/deny/revoke/outage/misbinding vectors trên provider thật, sanitized evidence counts/versions/revisions và direct TCP trước nâng status. Không dùng test grant data làm production authority hoặc gửi real tokens/secrets vào tài liệu.

**Có thể bắt đầu M5-C consumer code ngay** theo quyền kỹ thuật Patient: implement DoctorClientPort và AppointmentClientPort strict adapters; pin wire/version/projection/status/credentials và map missing/inactive/mismatch422 vs technical503. Giữ lookup ngoài local write transaction, không dùng scope grant để bỏ reference validation, không tự complete/cancel Appointment. Real B-04/B-05 vẫn cần provider compatible; actual identity/scope authority verification và B-02/Gateway/Frontend integration cũng còn. Canonical OAS503 discrepancy, shared adoption, D-15 cutover và remote post-check TOCTOU chưa được tự tuyên bố giải quyết.

Rollback M5-B: tắt scope-authority/enabled rồi restart→deny fallback403; identity giữ behavior M5-A, không mở access. Không chạy v1 binary hoặc rollback/migrate DB. Không sửa module/provider/shared contract/Git history trong task.

