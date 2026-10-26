# M5-D — Real PT/CT runbook v1

24/10/2026. Scope: B-04/B-05 **read-only wire + actual production reference adapter**, không whole Patient trust chain, Gateway/Frontend hoặc migration. [Provider prerequisites](11_PROVIDER_COMPATIBILITY_HANDOFF.md). Trong phiên này không có provider compatible/configured; profile đã fail preflight, zero outbound requests. Không trỏ harness vào endpoint có dữ liệu thật.

## 1. Điều kiện vào

- Java21/Maven, Patient source/contracts pin như [evidence](../../evidence/m5-d-verification.json).
- Provider owner cung cấp executable Doctor/Appointment Boot JAR thật, source commit/hash, deployed artifact/hash/config mapping và build/runtime version. Harness kiểm Boot controller entry/JAR SHA; **hash file local không tự chứng minh remote đang chạy cùng binary**. Deployment evidence phải là evidence thực của operator (deployment manifest/process/container digest/CI record), không tự viết “REAL_BINARY” cho mock.
- Isolated DB/environment riêng với synthetic fixtures, không legacy/shared DB, seed disabled hoặc explicit test provisioning bởi owner. Harness không có datasource, không seed/migrate/start provider. Default regression vẫn tạo PG16.10 mới dưới target/.
- Provider triển khai exact protected contracts, HTTPS chain/hostname trust, least privilege Basic client riêng từng boundary. Provider-side positive/negative auth tests và fixture manifest đã có.
- Hai credentials qua protected environment/secret references: authorized client, invalid/revoked/non-authorized negative client. Không thử password thật của người khác. Negative ID/secret khác positive; không reuse Auth/scope/reference khác.
- Owner xác nhận được phép read synthetic IDs trong manifest; không dùng arbitrary guessed/production IDs. Các example IDs bên dưới chỉ là vector template, **chưa được provision**, không là actual fixture evidence.
- CA trust: JVM default hoặc PKCS12 external trust-store. Không disable certificate/hostname check, không HTTP, redirect/proxy workaround hoặc provider stub.

## 2. Discovery không gửi network

Từ repository root, PowerShell7:
~~~powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File ClinicAppointmentBE/services/patient-service/scripts/Discover-RealProviders.ps1
~~~

ExecutionPolicy flag chỉ cho process đọc script đã review, không sửa policy máy. Script in env-presence booleans, local JAR hashes, baseline-entry drift; không in secret/endpoint, inspect commandline hoặc start provider. Một shell Windows PowerShell mặc định đã từ chối script theo execution policy trong phiên; rerun PowerShell7 process-scoped thành công.

M5-D thay pom + helper IT nên drift từ baseline M5-C của hai file đó là dự kiến; initial pre-edit digest đã MATCH. Discovery output không phải runtime proof.

## 3. Protected configuration

Tất cả là environment prefix PATIENT_REAL_PROVIDER_; không dùng -Dsecret trên commandline. Chọn **một boundary mỗi run**:

| Suffix | B-04 | B-05 |
|---|---|---|
| BOUNDARY | B-04 | B-05 |
| COLLECTION_URI | HTTPS origin + /api/v1/doctors | HTTPS origin + /internal/v1/appointments |
| CLIENT_ID / CLIENT_SECRET | Dedicated authorized Doctor client | Dedicated authorized Appointment client |
| INVALID_CLIENT_ID / INVALID_CLIENT_SECRET | Known invalid/revoked/unauthorized test client | Independent negative client |
| BINARY_PATH | Actual deployed executable Doctor JAR local evidence copy | Actual Appointment JAR |
| DEPLOYMENT_EVIDENCE_PATH | Protected JSON evidence file described below | Same format, correct Appointment binary |
| VECTOR_PATH | Owner-provisioned Doctor manifest | Owner-provisioned Appointment manifest |
| TRUST_STORE / TRUST_STORE_PASSWORD | Optional paired PKCS12 CA trust | Optional independent CA trust |

Endpoint validate exact route/prefix, no query/userinfo/fragment/trailing slash; IDs positive safe decimal. Defaults fixed bounded1s connect/3s full exchange/64KiB body/serial requests, TLS1.2/1.3, no redirects/application retry/cache. Each vector may perform two GETs (raw status/schema then production adapter), never mutations. No per-read snapshot consistency promised; stable synthetic fixtures required.

Deployment evidence **template, not proof** (closed JSON, max1MiB):
~~~json
{
  "providerKind": "REAL_BINARY",
  "isolatedSynthetic": true,
  "binarySha256": "<actual SHA256 of deployed provider JAR>",
  "endpointSha256": "<SHA256 UTF8 exact configured collection URI ASCII string>",
  "sourceCommit": "<actual 40 lowercase hex provider source commit>",
  "deploymentId": "<actual deployment evidence UUID>"
}
~~~

External operator record referenced by deploymentId must tie runtime to same binary/source/config/test environment. Keep actual URLs/credential secrets/private PKCS12 outside repository/evidence. Harness reports only hashes and fixed case names; sourceCommit is metadata, not Git mutation. A commit hash alone cannot describe uncommitted provider source—add source/build manifest in operator deployment record and inspect before signoff.

## 4. Fixture vectors

Copy/edit templates **outside repository** for actual provider-provisioned synthetic fixtures:

- [Doctor template](contracts/real-doctor-vectors-v1.example.json): ACTIVE,INACTIVE,MISSING,INVALID_CREDENTIAL,WRONG_VERSION_REQUEST mandatory.
- [Appointment template](contracts/real-appointment-vectors-v1.example.json): VALID,CANCELLED,PATIENT_MISMATCH,DOCTOR_MISMATCH,MISSING,INVALID_CREDENTIAL,WRONG_VERSION_REQUEST mandatory.

Schema: version integer1, boundary B-04/B-05, vectors array. Each vector closed fields case/resourceId/patientId/doctorId. IDs 1..9007199254740991. Doctor vectors patientId/doctorId null. Appointment VALID/CANCELLED/mismatches need expected parent UUIDv4 lowercase + expected doctor safe ID; other cases use null/null.

Mismatch vectors: expected patient/doctor are the *target Patient medical request*, while returned stored reference differs in exactly indicated relationship. Wire itself remains well formed; production adapter preserves facts. Harness asserts matching/mismatching facts, not application422 through a Patient HTTP mutation. Actual422/no-write whole-chain tests remain NOT EXECUTED.

Optional real controlled fault vectors: WRONG_RESOURCE,WRONG_VERSION_RESPONSE,TECHNICAL_5XX,INVALID_PAYLOAD; Appointment also UNKNOWN_STATUS (PENDING included). Missing optional fixtures appear NOT EXECUTED, not JUnit skipped. Only provide if real binary/environment supports safe controlled response behavior; never add fake provider/proxy/server to claim real compatibility. VALID with PENDING/Long/full DTO **fails**, not normalized.

## 5. Execution

Normal regression (no real provider lookup, no harness skipped tests):
~~~powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp
~~~

After environment + deployment/fixtures are genuinely ready:
~~~powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am -P real-provider-contracts verify -B -ntp
~~~

Run once per boundary, archive reports between runs to separate owner-controlled evidence directories. Profile compiles harness then runs default suites plus RealProviderContractCheck in separate Failsafe execution/report/summary; no skip flags. Missing/invalid preflight→build failure with BLOCKED observations before network. Provider response/assertion mismatch→REAL PROVIDER FAIL, sanitized output, build failure. Do not interpret intentional preflight failure as a passing provider test.

Reports:
- target/surefire-reports, target/failsafe-reports: regression, existing TCP with doubles remains STUB TCP PASS.
- target/real-provider-reports/observations.json + TEST-com.fclinic.patientservice.contract.RealProviderContractCheck.xml + failsafe-summary.xml: opt-in run. Current observations are preflight BLOCKED, cases[].
- Archive observations + SHA256 of each report, JAR, exact source/config-with-secret-values-excluded manifest, local wire schemas/canonical OAS, fixture manifest, operator deployment evidence. Avoid full system/env dumps, raw provider body/headers or commandline secrets. Review JUnit XML/system properties before sharing.

## 6. Acceptance assertions và interpretation

| Case | Required observation |
|---|---|
| ACTIVE / INACTIVE | Raw200/version/schema; actual RemoteDoctorClientAdapter returns exact id/active. Inactive is valid provider fact, Patient application422 independently tested component layer |
| VALID | Raw200/4-field UUID/status projection; actual RemoteAppointmentClientAdapter matches patient/doctor/id; non-CANCELLED not a visit grant |
| CANCELLED / MISSING | Exact domain404+marker→empty, or cancelled fact. No generic404 acceptance. Patient business422 is outside this read-only harness |
| INVALID_CREDENTIAL | Actual provider401/403, JSON error/no resource data; production consumer503. Cannot pass merely because client throws503/network unavailable |
| WRONG_VERSION_REQUEST | Provider400/406 + UNSUPPORTED_REFERENCE_CONTRACT/no data, cannot blindly reflect marker |
| Controlled technical/malformed/wrong resource/status/version | Raw fault observed and production adapter rejects503; failure details/body/secret not emitted |
| No unexpected disclosure | Closed data shape; denied/error data:null/fieldErrors[] and no obvious token/URL/stack markers. Free-text PHI/access logs require independent provider review; structural test is not complete privacy proof |

Raw probe validates HTTPS/host/chain with configured CA and bounded response; production adapter revalidates same wire independently. No explicit real bad-certificate/hostname fault environment was available; those cases are NOT EXECUTED, prior transport doubles are historical evidence only. No automatic application retry; HttpClient may have protocol-level behavior, do not claim single wire packet from code alone.

“REAL PROVIDER PASS” rows require actual inspected binary + operator-attested deployment and real requests. Harness cannot cryptographically establish remote runtime identity from a local JAR; reviewer must validate deployment record. Unsupported/mock/generator binaries must never be attested as real. All mandatory cases + review are needed for boundary signoff; absent fault/TLS cases and provider-specific authorization tests remain exclusions. Neither boundary wire PASS proves Auth/scope trust, clinical mutation/no-write or B-02 Appointment consumer.

## 7. Whole-chain follow-up và cleanup

- Following real wire pass, provision actual Auth/current mapping/scope authorities and isolated v2 Patient DB; run authorized create/no-write/T-REF-01..06/IT-02/10/11/12 on actual Patient+providers, capture DB versions/revisions/counts and request correlation. Not implemented/executed by this standalone harness.
- B-02 requires actual Appointment consumer changes + original human authorization + ACTIVE validation before side effects. Gateway/Frontend preservation/E2E belongs M5-E/F; not part of this task.
- Stop only isolated processes you explicitly launched by documented owner PID/deployment. This harness starts none, no provider fixture cleanup/write endpoints. Owner owns synthetic DB cleanup/restore separately.
- Remove process-scoped secret env vars/end shell; preserve redacted evidence. Do not delete shared volumes/data, run migration, run v1 on target schema, reset/clean Git or restore legacy binaries to “undo” read-only verification.

