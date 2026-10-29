# P-FINAL — nối real Auth/WorkScope bằng configuration

26/10/2026. **Procedure prepared, real Auth/scope NOT EXECUTED.** Không tạo issuer hoặc permanent mock runtime. Đúng selected contract thì không cần rewrite Patient domain/policy/writer/adapters. Nếu wire khác, cần versioned contract decision, không nới decoder hoặc nhận hai formats.

## 1. Entry gate và người chịu trách nhiệm

Auth owner cung cấp [handoff16](16_P_FINAL_AUTH_CONSUMER_HANDOFF.md); scope owners cung cấp [handoff17](17_P_FINAL_WORKSCOPE_HANDOFF.md). Integration Lead/QA review actual source→binary/image→runtime PID/container/config/certificate/identity-store, issuer/audience và quyền fixtures trước khi gọi. Một JSON tự khai REAL_BINARY hoặc env `REVIEWED=true` không thay review này.

Chỉ fresh synthetic databases/owned processes, loopback/private isolated network; không legacy/shared DB hoặc production records. Review source hashes/contracts theo [evidence](../../evidence/patient-p-final-verification.json). Không mang private keys, tokens, secret values vào Git/report. Giữ booking/default relay/SMTP/delivery off. Không cần triển khai Auth thuộc Patient task.

## 2. Provision và freeze fixtures ngoài repository

Auth owner tạo USER A/B đã link hai Patient UUIDv4 khác nhau, unlinked USER, linked Doctor, registered Appointment SERVICE và unknown/nonregistered SERVICE. Token fixtures phải được **provider thật phát hành/xử lý**, không do test table trả context cố định. Provision real account disable/revoke workflows và expired/wrong issuer/audience credentials. Backend clients Patient/Appointment tách nhau và tách reference/scope credentials.

DB owner tạo Patient v2 DB mới và app role riêng, áp approved empty schema một lần bằng owner profile. Ví dụ chỉ sau xác minh PGSERVICE/PGPASSFILE ngoài Git trỏ đúng instance/database vừa tạo:

```powershell
psql -X --set ON_ERROR_STOP=1 --file ClinicAppointmentBE/services/patient-service/src/main/resources/db/schema/V001__patient_v2_empty.sql
```

Không dùng Hibernate update/create hoặc v1 binary. Synthetic profile A/B có thể provision bằng controlled SQL trên DB mới trước snapshot khi receptionist scope chưa có; records/actors phải đúng constraints, ghi fixture provenance, không tạo production seed endpoint. Auth linking là thao tác riêng do Auth owner thực hiện, không tự link theo phone/email. SQL file SHA1500fb0c94d9a504237889201ce900185c771bc80403d484a1d9d47384536ef6.

## 3. Provider/adapter contract stage — explicit opt-in

Maven profile `real-auth-contracts` đã có trong Patient; default verify không chạy probe. External prefix `FCLINIC_REAL_AUTH_PATIENT_` cần ENDPOINT,ISSUER,AUDIENCE,CLIENT_ID,CLIENT_SECRET,USER_TOKEN,USER_SUBJECT,PATIENT_UUID,DOCTOR_TOKEN,DOCTOR_ID,SERVICE_TOKEN,SERVICE_SUBJECT,SERVICE_NAME,SERVICE_AUTHORITIES,UNLINKED_TOKEN,UNKNOWN_SERVICE_TOKEN,INVALID_TOKEN,EXPIRED_TOKEN,INACTIVE_TOKEN,REVOKED_TOKEN,WRONG_ISSUER_TOKEN,WRONG_AUDIENCE_TOKEN; optional TRUST_STORE/TRUST_STORE_PASSWORD. EVIDENCE_DIR ngoài repo phải tồn tại, private; raw tokens không có Bearer prefix.

```powershell
# Chỉ chạy sau manual provenance review; câu lệnh không chứa token/secret.
# FCLINIC_REAL_AUTH_EVIDENCE_DIR phải là thư mục mới bên ngoài repository, ACL private.
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am -P real-auth-contracts verify -B -ntp
```

Nếu dùng [preflight runner của task trước](../../../../../docs/integration/run-m5-d3a-auth-probe.ps1), nó mặc định không execute; đọc required external provenance/fixture inputs trước. Runner là công cụ tích hợp riêng, không dependency production module. Profile trực tiếp vẫn khả dụng nếu chỉ checkout Patient branch không có runner.

Probe gọi actual IntrospectionIdentityVerifier, không start fake Auth/provider. Mỗi case là assertion trên adapter, **không tự chứng minh endpoint Patient authorization, live revocation hoặc provider authenticity**. Thiếu inputs→build failure/BLOCKED, không Assume/skip. Archive private XML + sanitized probe JSON, source/JAR/config/TLS hashes. Không log raw request/response/token.

## 4. Actual Patient runtime — self-read first

External protected settings: PATIENT_V2_DB_URL/USERNAME/PASSWORD; PATIENT_AUTH_INTROSPECTION_URI/ISSUER/AUDIENCE/CLIENT_ID/CLIENT_SECRET; optional CA-only trust-store/password và exact SERVICE registry theo handoff16. Inbound TLS phải được cấu hình qua protected server.ssl settings hoặc approved secure deployment boundary; không dùng loopback HTTP test exception làm production proof.

```powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp
java -jar ClinicAppointmentBE/services/patient-service/target/patient-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=identity-introspection
```

Đây là command tương lai sau gate, **không được chạy target với datasource mặc định v1**. Scope vẫn disabled/deny; genuine USER self-read không cần WorkScope. Positive SERVICE/admin/clinical còn chờ authority; không fake scope để làm200.

Guarded direct-port smoke bằng PowerShell7, fixtures/endpoint supplied external; snippet chưa chạy trong P-FINAL:

```powershell
if ($env:PATIENT_PFINAL_EXECUTE -ne 'true') { throw 'Real provider review and explicit opt-in required' }
$base = [uri]$env:PATIENT_PFINAL_BASE_URI
if ($base.Scheme -ne 'https' -or $base.UserInfo -or $base.Query -or $base.Fragment) { throw 'Exact HTTPS Patient base URI required' }
foreach ($name in @('PATIENT_PFINAL_USER_A_TOKEN','PATIENT_PFINAL_PATIENT_A','PATIENT_PFINAL_PATIENT_B')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw 'External synthetic fixture missing' }
}
$headers = @{ Authorization = 'Bearer ' + $env:PATIENT_PFINAL_USER_A_TOKEN }
$own = Invoke-WebRequest -Uri ($base.AbsoluteUri.TrimEnd('/') + '/api/v2/patients/' + $env:PATIENT_PFINAL_PATIENT_A) -Headers $headers -SkipHttpErrorCheck -TimeoutSec 10
if ([int]$own.StatusCode -ne 200 -or ($own.Content | ConvertFrom-Json).data.id -ne $env:PATIENT_PFINAL_PATIENT_A) { throw 'Self read failed' }
$headers['X-Role'] = 'ROLE_STAFF'
$headers['X-Patient-Id'] = $env:PATIENT_PFINAL_PATIENT_B
$other = Invoke-WebRequest -Uri ($base.AbsoluteUri.TrimEnd('/') + '/api/v2/patients/' + $env:PATIENT_PFINAL_PATIENT_B) -Headers $headers -SkipHttpErrorCheck -TimeoutSec 10
if ([int]$other.StatusCode -ne 403 -or ($other.Content | ConvertFrom-Json).code -ne 'FORBIDDEN') { throw 'Cross-patient denial failed' }
'Direct self/cross-patient assertions passed; token/provider lifecycle still requires separate evidence'
```

Không dùng SkipCertificateCheck/trust-all; CA đã duyệt phải được trust. Không print `$headers`, `$own.Content` hoặc env dump vào evidence. Bổ sung structured-envelope assertions theo Error schema và snapshot DB trước/sau mọi denied mutation; snippet trên chỉ read smoke, không đủ toàn acceptance.

## 5. Lifecycle, outage và authorization acceptance

| Stage | Exact action | Required evidence / expected |
|---|---|---|
| Authentication negatives | Missing/malformed bearer; genuine invalid/expired/inactive/revoked/wrong issuer/audience |401 UNAUTHENTICATED, generic envelope; no Patient writes. |
| Mapping/permissions | A→B, unlinked self, SERVICE→human/clinical, unknown registry, forged browser fields |403 for authenticated policy denials; malformed mapping/provider response503; no unauthorized resource/body disclosure or writes. |
| Current disable/revoke | Auth owner thực hiện actual supported admin/revocation operation; repeat **same token** | Previously200 becomes401 on next request without Patient restart; capture provider/store state, no positive cache claim from fixtures alone. |
| Technical credential | Controlled revoke/change dedicated backend credential, not human bearer | Provider rejects backend; Patient503, no rights fallback. Restore protected configuration safely. |
| Owned Auth outage | Stop only owned Auth process or controlled dependency path | Patient503 within bounded deadline; no stack/token/internal URL; no writer/reference calls and DB unchanged. Restore owned provider. |
| Scope current state | Enable real authority profiles with authentic grants then revoke assignment/visit | deny403 for business; unavailable503; no-op and changed writes denied, counts/content/versions/revisions unchanged. |

Snapshot4tables with owner-controlled read-only audit tooling on isolated DB; record counts/versions/revisions and digest of synthetic row content. Do not store raw clinical/audit snapshots in shared reports. Remote snapshot races after a final grant are not distributed atomicity.

## 6. Add scopes/references without rewriting core

Sau real Auth self boundary verified, enable `scope-authority` chỉ khi administrative/clinical owners đã cung cấp exact endpoints, credentials, current assignments/encounters và [scope vectors](17_P_FINAL_WORKSCOPE_HANDOFF.md). Sau đó mới positive staff/Doctor/SERVICE tests. Linked create dùng thêm two reference profiles; walk-in chỉ skip Appointment, vẫn Doctor/scope/valid visit.

Doctor/Appointment independent actual-binary rechecks từ repository root:

```powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/doctor-service -am verify -B -ntp
mvn -f ClinicAppointmentBE/pom.xml -pl services/appointment-service -am verify -B -ntp
```

Existing provider ITs create owned actual Boot JAR/HTTPS/PG runtime and call Patient `real-provider-contracts` harness while alive. B-04/B-05 each verified separately; they do not establish actual Auth/scope clinical mutation chain. For externally deployed providers, use [runbook12](12_REAL_INTEGRATION_RUNBOOK.md) and archive per-boundary reports before another run overwrites target/current.

## 7. Regression, evidence, shutdown

```powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp
mvn -f ClinicAppointmentBE/pom.xml verify -B -ntp
```

Capture actual test XML F/E/S, logs/build/source/test/contracts/JAR hashes, provider builds/PIDs/start/config/TLS/store identity, per-vector HTTP expected/actual and no-write digests. Classify REAL PROVIDER, COMPONENT/TCP WITH AUTH/SCOPE DOUBLE, MIXED, BLOCKED, NOT EXECUTED separately. Logical vectors inside aggregate test are not additional JUnit cases.

Stop only owned runtime/PG/provider processes; save evidence first, delete only owned ephemeral private TLS stores after verifying paths. Preserve source/backups/historical evidence. Keep real tokens/private keys out of reports. No Git commit/push, migration, shared deployment, booking, SMTP or default relay activation is part of this runbook execution scope without separate task authorization.
