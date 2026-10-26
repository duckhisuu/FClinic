# M5-A — production identity adapter

Ngày thực hiện: 24/10/2026, Asia/Saigon. Phạm vi: Patient Service. Quyền chọn giải pháp kỹ thuật được người dùng ủy quyền trực tiếp trong task M5-A. Các ràng buộc “chờ approval trước khi code M5-A” của audit 01–06 là snapshot trước ủy quyền, không còn chặn implementation này. Không thay trạng thái quyết định trong approved pack.

## 1. Quyết định kiến trúc

**ID PAT-ID-001, trạng thái PROJECT_DECIDED: một cơ chế duy nhất — online token introspection qua HTTPS có xác thực backend client.** Không xác minh JWT offline, không parse JWT payload, không cache positive activation. Mỗi request v2 có bearer hợp lệ về syntax đều gọi Auth trước khi thiết lập trusted context. Token có thể là opaque hoặc JWT; provider phải xác minh token theo cơ chế phát hành của mình.

Căn cứ: [Auth draft](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/openapi/auth.openapi.json) operationId validateCaller và CallerContext; shared Authentication_Authorization/Error_Handling_Contract; source [PatientPolicy](../../src/main/java/com/fclinic/patientservice/application/security/PatientPolicy.java) và [IdentityVerifierPort](../../src/main/java/com/fclinic/patientservice/application/port/out/IdentityVerifierPort.java). Parent POM và services thực tế chỉ có patient/doctor/appointment/notification; không có Auth runtime implementation. Quét source không thấy implementation validateCaller ngoài draft. Không có endpoint/credential môi trường thật được cung cấp.

Chọn [RFC 7662 §2](https://www.rfc-editor.org/rfc/rfc7662.html#section-2) vì cần activation/revocation và mappings hiện tại từ một authority; backend client được xác thực độc lập với token đang introspect. RFC là căn cứ protocol; các trường extension, limits và registry dưới đây là quyết định Patient của phiên này.

Alternatives considered:

| Phương án | Đánh giá |
|---|---|
| JWT offline + JWKS | Chưa có issuer/JWKS/claim profile chạy thật; vẫn cần online activation/mapping. Tạo hai nguồn context hoặc stale grant phức tạp hơn, chưa chọn |
| Forward bearer tới draft validateCaller | Draft dùng bearer để validate nhưng chưa giải quyết credential của backend caller và provenance serviceName; không giả định endpoint đã tồn tại |
| HTTPS introspection + backend Basic + strict local response | Được chọn; activation online, không chia sẻ signing key; backend secret riêng và endpoint cấu hình cố định; yêu cầu Auth implement wire profile mới |
| Browser identity headers hoặc dev token | Không là nguồn identity; không triển khai |

**CODE COMPLETE / COMPONENT VERIFIED / TCP VERIFIED** trong phạm vi adapter và tests dưới đây. **PROVIDER_IMPLEMENTED: chưa có bằng chứng. PROVIDER VERIFIED / INTEGRATION VERIFIED: NOT EXECUTED.** Không đánh dấu M5 hoặc service DONE.

## 2. Local wire contract v1, không thay draft

[Schema v1](contracts/patient-identity-introspection-v1.schema.json), ID urn:fclinic:patient:identity-introspection:1; response discriminator contractVersion=patient-identity-introspection/1. SHA-256 và manifest trong [evidence mới](../../evidence/m5-a-verification.json). Đây là contract consumer PROJECT_DECIDED; không phải shared ADOPTED hoặc bằng chứng provider support.

Request tới **exact URI do deployment cấu hình**, không có default endpoint:
- HTTPS TLS1.2/1.3; hostname/certificate chain verification luôn bật.
- POST, Content-Type application/x-www-form-urlencoded, Accept application/json.
- Authorization Basic: client ID và secret riêng của Patient, từng thành phần URL-form encoded rồi Base64 theo RFC6749 §2.3.1.
- Form đúng token=<original opaque bearer>&token_type_hint=access_token. Không dùng human bearer làm backend credential, không gửi token qua URL/query. Không forward browser headers.
- Backend identity chỉ được introspect access tokens cho Patient audience, không là quyền USER hoặc clinical.

Response200 application/json, unwrapped như RFC7662. Inactive/invalid/revoked: **chỉ** {"active":false}. Active: đủ 11 fields closed schema: active=true, contractVersion, iss, aud, exp, sub, principalType, userId, patientId, doctorId, authorities. Không chấp nhận draft wrapper hoặc cả hai format.
- iss exact configured issuer; aud string hoặc unique nonempty array phải chứa configured Patient audience; exp integral epoch seconds và strictly > injected Clock now, không leeway/cache.
- Auth phải kiểm credential validity, revocation, current account/client activation, token audience/issuer/expiry và current trusted linking trước khi trả active=true; không trả active=false để che outage.
- USER: opaque sub1..255 không control chars, required positive safe integer userId; patientId UUIDv4 lowercase hoặc null; doctorId positive safe integer hoặc null. Không suy các ID bằng nhau hoặc link qua phone/email. Unlinked user vẫn authenticated nhưng không có self/doctor mutation grant.
- USER authorities chỉ ROLE_PATIENT/ROLE_DOCTOR/ROLE_STAFF/ROLE_ADMIN, unique, tối đa32; role không tự cấp scope. Unknown vocabulary là protocol error503, cần contract version mới để mở rộng.
- SERVICE: userId/patientId/doctorId bắt buộc null; nonempty SVC_* authorities, không human ROLE_*. No serviceName trên wire. Exact issuer-scoped sub phải có trong server registry và token authorities nằm hoàn toàn trong registry ceiling; thiếu registry/vượt grant→403.
- Duplicate keys, trailing JSON, unknown/missing fields, null/type/range sai, duplicate authorities/audiences, malformed UTF-8/JSON hoặc quá giới hạn→503. Schema mô tả shape; decoder còn kiểm semantic/Clock/registry/size/control chars, không tuyên bố JSON Schema validator đầy đủ.
- 401/403 từ Auth endpoint là lỗi **backend credential/permission**→Patient503. Invalid end-user token dùng200 active=false→Patient401. Đây là khác biệt cố ý với draft validateCaller, cần provider thực hiện profile mới; adapter không fallback sang draft.

Auth draft SHA-256: 55545ccc1e8cdd4aec84ea8d9a55938856c3151f7d1c23e5126b60efefc067be, version1.1-proposed-pack1, được giữ nguyên.

## 3. USER/SERVICE và F-07 serviceName provenance

[Decoder](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/identity/IntrospectionDecoder.java) chỉ tạo TrustedCaller sau HTTPS Auth response đã vượt strict checks. Không deserialize wire thẳng vào TrustedCaller.

Trust chain SERVICE: token → authenticated Auth HTTPS endpoint → validated active SERVICE principal/sub/issuer/audience/expiry → **exact sub lookup trong registry Patient được quản lý phía server** → fixed binding.name → TrustedCaller.serviceName. Không split sub; không dùng X-Service-Name/X-Role/X-User-Id, body hoặc unverified JWT. Auth phải bảo đảm sub unique/stable trong issuer namespace và principalType là authority assertion, không lấy từ input caller.

Registry binding appointment-service có ceiling SVC_PATIENT_READ (local v1 vocabulary). F-07 được giải quyết về code/provenance; mapping của machine principal thật chưa provision/verify. PatientPolicy giữ nguyên: chỉ appointment-service + WorkScopePort.appointmentProfileLookup mới có profile detail; SERVICE không search/create/update hoặc đọc clinical. Registry không thay scope và không chứng minh Appointment đã authorize original human trước SERVICE lookup/slot hold. B-02 vẫn phải kiểm tra ở Appointment thật.

USER role/mapping không mở administrative/clinical scope. WorkScopePort production vẫn deny; principal doctorId phải khớp body, author subject+doctorId và current scope giữ nguyên M0–M4. Audit actor lấy sub từ verified context, không client fields.

## 4. Implementation files và DI

| File trong module | Vai trò |
|---|---|
| [BearerTokens](../../src/main/java/com/fclinic/patientservice/application/security/BearerTokens.java) | Một Authorization header, case-insensitive Bearer + đúng một space, RFC6750 token syntax, max8192; reject comma/colon/whitespace/embedded padding |
| [PatientAuthentication](../../src/main/java/com/fclinic/patientservice/api/security/PatientAuthentication.java) | Interceptor v2 tại direct port; không đọc identity headers; typed errors qua existing canonical Patient handler |
| [IdentitySettings](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/identity/IdentitySettings.java) | Typed immutable settings, unknown config rejected, validation, toString redacted, registry defensive copies |
| [IntrospectionIdentityVerifier](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/identity/IntrospectionIdentityVerifier.java) | JDK21 HttpClient, HTTPS, authenticated form POST, bounded streaming body, deadline/concurrency, technical error translation; no new dependency |
| [IntrospectionDecoder](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/identity/IntrospectionDecoder.java) | Strict closed versioned response, trust/mapping checks, no raw token parsing |
| [PatientIdentityConfiguration](../../src/main/java/com/fclinic/patientservice/config/PatientIdentityConfiguration.java) | enabled=true chỉ chọn real adapter; lifecycle closes HTTP client |
| [PatientConfiguration](../../src/main/java/com/fclinic/patientservice/config/PatientConfiguration.java) | disabled/missing enabled chỉ chọn fallback503; scope/reference fallbacks giữ nguyên |
| [Profile](../../src/main/resources/application-identity-introspection.yml) | Explicit opt-in, endpoint/issuer/credentials không có defaults |

Không thêm Spring Security stack hoặc đổi Boot/Cloud/Java/POM. HandlerInterceptor hiện có tiếp tục enforce tám operations và /api/v2/** trên actual servlet server; policy/use cases không đổi. Exactly-one verifier bean được context-test cả disabled và enabled. Enabled nhưng config thiếu/sai→startup fails closed; disabled có bearer syntax hợp lệ→503, missing/malformed bearer→401. Provider down không làm permissive fallback.

## 5. Configuration, secrets, transport

Bật profile identity-introspection chỉ khi đã provision provider compatible và DB v2 được chuẩn bị riêng:

~~~powershell
java -jar ClinicAppointmentBE/services/patient-service/target/patient-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=identity-introspection
~~~

Cần PATIENT_V2_DB_URL/USERNAME/PASSWORD như README; không trỏ legacy/shared DB. Cần PATIENT_AUTH_INTROSPECTION_URI, PATIENT_AUTH_ISSUER, PATIENT_AUTH_CLIENT_ID, PATIENT_AUTH_CLIENT_SECRET. PATIENT_AUTH_AUDIENCE mặc định patient-service là quyết định local, phải khớp token/provider; không tự suy audience từ token. Secret qua deployment secret store/environment, không commit value/command-history secret.

Protected external Spring config cho optional custom CA PKCS12 trust-store/trust-store-password và service registry (operator thay exact subject được Auth cấp; không có entry mặc định):

~~~yaml
patient:
  identity:
    services:
      "[exact-authenticated-machine-subject]":
        name: appointment-service
        authorities:
          - SVC_PATIENT_READ
~~~

Map key cần bracket để bảo toàn opaque subject gồm ký tự đặc biệt. Registry trống cho phép valid USER theo policy nhưng mọi SERVICE bị403. Không cho caller sửa config. Custom truststore chứa CA/cert được tin cậy; không trust-all hoặc tắt hostname verification. Default sử dụng JVM trust roots. Rotation: deployment cấp credential/trust/registry mới rồi rolling restart; không hot reload hoặc automatic fallback credential cũ. Provider cần hỗ trợ overlap/revoke theo release riêng, không khẳng định đã có.

PROJECT_DECIDED budgets: connect1s, total exchange3s (bao gồm body completion, future cancel khi deadline), no waiting concurrency32, response64KiB; JSON depth8, string8192, number20 digits. Timeout configurable PATIENT_AUTH_CONNECT_TIMEOUT/EXCHANGE_TIMEOUT; validated100ms..30s và connect<=exchange. Response limit configurable1KiB..256KiB; concurrency1..256. Quá capacity→503. Không application retry, redirect hoặc positive/negative credential cache. Provider phải no-store sensitive responses; client không cache. Không claim budgets shared đã adopt hoặc SLO đã đo.

Không log request headers/form/token/credential/provider payload/context. Không đưa remote exception/cause/URL vào PatientError. Không bật HTTP wire logging/request-body capture cho auth; gateway/APM/access-log operator phải redact Authorization/body và quản lý secret files/heap dumps. Metrics/correlation/RequestId chain chưa được thêm ở M5-A; F-05 vẫn còn cho integration rollout.

## 6. Error mapping

| Tình huống | Patient HTTP/code |
|---|---|
| Missing/multiple/malformed bearer | 401 UNAUTHENTICATED; không gọi Auth |
| Active=false, expired, wrong issuer/audience | 401 UNAUTHENTICATED |
| Valid USER thiếu role/self/doctor/scope/author, SERVICE unknown registry/excess ceiling | 403 FORBIDDEN |
| Auth TLS/network/timeout/HTTP error/technical credential failure/oversize/invalid wire | 503 DEPENDENCY_UNAVAILABLE |
| Disabled verifier, syntax-valid bearer | 503 DEPENDENCY_UNAVAILABLE |
| Enabled nhưng config sai/thiếu | Không startup; không có permissive runtime |

Envelope giữ status/message/data:null/code/fieldErrors, generic codes; không gửi PHI/stack/token/URL. D-21 Patient OAS chưa liệt kê503 mọi operation vẫn là discrepancy, không sửa canonical artifact để hợp thức hóa.

## 7. Test evidence thực tế

Command từ root: mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp.
Expected: 58 baseline không regression, tests mới và real TCP pass, chỉ fresh isolated PostgreSQL.
Actual cuối: **BUILD SUCCESS/exit0, 2026-10-24T00:46:06+07:00,45.676s; 114 tests =82Surefire +32Failsafe, failures0/errors0/skipped0**. Giữ58baseline, thêm56tests. [Machine evidence](../../evidence/m5-a-verification.json) có source/test/contract/build/report hashes; source manifest digest aa126b3bc7a5b855296b906e443dde3569f0372efdd876b48341996f2f74a42e. Reports target được ghi đè mỗi lần chạy; evidence pin lần này riêng, historical verification-summary.json giữ nguyên.

| Suite | Assertions / layer |
|---|---|
| Existing 5 suites, 58 tests | Source tests không đổi; M0–M4 profile/medical, locks/races/no-op/atomic revision vẫn kiểm trên PG; PatientServiceIT còn MockMvc + identity double |
| [IntrospectionDecoderTest](../../src/test/java/com/fclinic/patientservice/identity/IntrospectionDecoderTest.java),30 | USER/SERVICE, exact registry/ceilings, inactive/expired/issuer/audience, unlinked account, safe IDs, missing/null/unknown/type/duplicate/trailing payload, forged serviceName |
| [IdentityConfigurationTest](../../src/test/java/com/fclinic/patientservice/identity/IdentityConfigurationTest.java),5 | Exactly-one DI/default closed/invalid config, secret-redacted settings, strict bearer syntax |
| [IntrospectionTransportTest](../../src/test/java/com/fclinic/patientservice/identity/IntrospectionTransportTest.java),9 | Real HTTPS to test-only server, authenticated form POST, body limit/content type/HTTP errors/no retry, deadline + latch/capacity, untrusted TLS/wrong hostname/backend secret, fresh activation |
| [PatientIdentityTcpIT](../../src/test/java/com/fclinic/patientservice/identity/PatientIdentityTcpIT.java),12 | Actual localhost RANDOM_PORT Spring servlet + real adapter + fresh PostgreSQL16.10; 401/403/503 envelope/no disclosure, forged/duplicate headers, self/wrong UUID/unlinked, SERVICE detail-only/scope, staff/admin/doctor denials, author/nested lookup, full PUT/no-op/stale, trusted actor/revisions |

TCP suite không MockMvc và không mock IdentityVerifierPort. Auth HTTPS test-only contract double, WorkScope/Doctor/Appointment test-only Mockito beans cho positive core paths; không chứng minh scope/provider thật. Cert/key được keytool sinh trong target/identity-test-tls-* cho fixture, không production keys hoặc fake JWT. Client credentials ngẫu nhiên trong test process. Synthetic Clock lấy registry 2026-10-20T03:00:00Z; DOB business timezone giữ Asia/Saigon. Cả hai PG suites tạo cluster mới target/patient-v2-it-* hoặc patient-identity-tcp-it-*, bind127.0.0.1/random port, apply approved empty-DDL, stop sau suite; không đọc DB env/dữ liệu cũ.

Hai test helpers mới: [IdentityFixtures](../../src/test/java/com/fclinic/patientservice/identity/IdentityFixtures.java), [TestAuthServer](../../src/test/java/com/fclinic/patientservice/identity/TestAuthServer.java). Không đưa helpers/cert/key vào main runtime. Tổng thay đổi source/config/tests so với historical manifest:3files cũ thay đổi (interceptor, port Javadoc, fallback condition),12files mới;37files cũ giữ nguyên, gồm cả5test classes. README/IMPLEMENTATION_STATUS/INTEGRATION_HANDOVER được cập nhật, thêm report07/schema local/evidence JSON; sáu audit docs và historical evidence không sửa.

Lịch sử lượt này: default sandbox Maven ban đầu không truy cập được dependency cache/network (“Permission denied: getsockopt”), chạy lại bằng Maven execution được cấp quyền; không đổi stack. Verify đầu112 assertions có2errors trong test fixture Mockito override, đã sửa bằng doReturn; không skip test. Verify tiếp112pass; bổ sung TLS protocol pin/hostname+backend-secret tests, rồi chạy final verify. Mockito dynamic-agent và existing deprecated Jackson API warnings còn, không che bằng skip.

Không suy test count thành coverage% hoặc full DoD. Real provider CT/PT, Gateway và FE E2E: **NOT EXECUTED**.

## 8. Provider rollout và M5-B tiếp theo

Để chạy actual provider/config:
1. Auth triển khai RFC7662 endpoint và local schema v1, current activation/mapping authority, credential scope; xác nhận sub namespace và semantics active=false vs technical errors. Gửi provider build/hash/URI/issuer/audience, TLS chain và synthetic USER/SERVICE vectors; không gửi production token/secret vào evidence.
2. Provision least-privilege Patient introspection client/secret và exact machine-subject registry; kiểm backend credential rotation/revocation. Auth phải validate queried token, không trả context của chính introspection client.
3. Chạy cùng vectors valid/invalid/expired/inactive/wrongissuer/audience/mappings/backendcredential/outage trên actual provider; direct Patient TCP với isolated target DB và default deny scopes. Self reads có thể verify độc lập; positive staff/clinical/SERVICE scope cần M5-B.
4. Record provider/consumer builds, hashes, redacted request/result, DB counts/revisions; chỉ khi đó nâng PROVIDER VERIFIED, sau whole trust boundary actual config mới INTEGRATION VERIFIED. Không tự suy từ stub pass.
5. Gateway vẫn cần route v2/forward original bearer/no identity headers/no mutation retry; Appointment phải human-authorize trước SERVICE validation/slot hold. Không sửa các module đó ở task này.

**Ready để bắt đầu code M5-B theo quyền kỹ thuật đã ủy quyền**: reuse verified TrustedCaller và port hiện có, thiết kế source of truth cho administrative scope tách clinical assignment/valid visit, typed503 cho scope outage và null vs empty search semantics. Nguồn scope/runtime provider hiện chưa có; không dùng authority/role/registry để tự grant clinical. Default scope/ref adapters vẫn fail closed. F-06/F-05, B-02/04/05/07 và M6 còn ngoài completion này. M6 inventory/backup/clone/writer drain/cutover không thực thi.

Rollback kỹ thuật của M5-A: tắt enabled/profile và restart → auth fallback503, không mở access; hoặc triển khai lại target v2 build đã kiểm tương thích cùng schema. Không chạy v1 binary trên v2 DB, không rollback DB hoặc migrate trong task này.

