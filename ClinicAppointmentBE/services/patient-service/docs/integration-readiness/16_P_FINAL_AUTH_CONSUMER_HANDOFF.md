# P-FINAL — handoff cho Auth owner

26/10/2026. **PATIENT AUTH CONSUMER READY / REAL AUTH PROVIDER PENDING.** Đây là yêu cầu bàn giao để teammate triển khai provider, chưa gửi hoặc được xác nhận adopt. Auth owner được chọn provider phù hợp đã được nhóm thống nhất; **Patient không yêu cầu Spring Authorization Server hay Keycloak**, không triển khai issuer/token issuance. Công nghệ đề xuất trong tài liệu kế hoạch cũ không phải điều kiện consumer.

## Contract freeze

[patient-identity-introspection-v1.schema.json](contracts/patient-identity-introspection-v1.schema.json), literal `patient-identity-introspection/1`, SHA-256 **`1ed4764c1480cc0e7776f36bdd0d6fbd9e97e6766a64dd827308dd2785b61088`**. PROJECT_DECIDED consumer profile, không tự chuyển canonical draft thành ADOPTED. [Decoder](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/identity/IntrospectionDecoder.java) là semantic authority phía consumer; nguồn/config/test hashes và actual tests ở [evidence](../../evidence/patient-p-final-verification.json), [acceptance](19_P_FINAL_ACCEPTANCE_REPORT.md).

### Request và transport

- Exact HTTPS URI do Auth owner cung cấp qua deployment config; không route mặc định được khẳng định tồn tại. POST `application/x-www-form-urlencoded`, Accept application/json.
- Dedicated Patient introspection backend client dùng Basic: **URL-form encode từng clientId và clientSecret**, ghép bằng dấu `:`, UTF8→Base64. Form `token=<URL-form-encoded raw original access token>&token_type_hint=access_token`. Không dùng incoming bearer làm backend identity, không đặt token trong URL.
- Backend client chỉ được introspect audience được cấp; Patient/Appointment/Doctor-reference/Appointment-reference/admin-scope/clinical-scope phải tách credentials. Không forward browser identity headers.
- TLS1.2/1.3, certificate chain + hostname verification; no redirect/application retry/token cache. Current consumer defaults connect1s/full exchange3s,64KiB response,32concurrent; bounds/config là Patient implementation budgets, chưa shared SLO. Capacity/timeout/TLS/network/error/malformed body→503.

### Exact responses

HTTP200 application/json, **unwrapped**, đủ11required fields khi active. Nullable fields vẫn phải xuất hiện. Ví dụ dữ liệu synthetic minh họa, không phải token hay response provider thật:

```json
{
  "active": true,
  "contractVersion": "patient-identity-introspection/1",
  "iss": "https://auth.example.invalid",
  "aud": "patient-service",
  "exp": 2000000000,
  "sub": "opaque-synthetic-user-subject",
  "principalType": "USER",
  "userId": 401,
  "patientId": "11111111-1111-4111-8111-111111111111",
  "doctorId": null,
  "authorities": ["ROLE_PATIENT"]
}
```

Invalid/expired/revoked/currently inactive queried credential phải HTTP200, **chỉ** `{"active":false}`. Không kèm identity metadata. Consumer trả401. Auth outage/mapping authority không thể xác minh là technical failure; không trả active:false để che unavailable.

Duplicate keys, unknown/missing fields, null/type sai, wrong contractVersion, malformed JSON hoặc response quá lớn→consumer503. Wrong exact issuer/audience hoặc exp<=Clock.now→401. `aud` string hoặc unique nonempty array chứa configured audience; exp integral epoch-seconds safe integer. Backend Auth401/403 là lỗi credential/quyền của **Patient backend client**, consumer503, không biến thành human401/403.

## USER và trusted linking

- Opaque stable sub1..255, không control chars; positive safe Long userId; Patient UUIDv4 lowercase/null; Doctor positive safe Long/null. Không suy ID bằng nhau, không link theo phone/email, không cho client tự chọn mapping.
- Roles đúng vocabulary `ROLE_PATIENT/ROLE_DOCTOR/ROLE_STAFF/ROLE_ADMIN`, unique max32; trusted current role assignment từ provider. Roles không tự tạo administrative/clinical grants.
- Unlinked USER có identity hợp lệ với nullable mapping, nhưng self/doctor policy403. Ambiguous/inconsistent mapping không được chọn phần tử đầu tiên hoặc fabricate default; owner cần persist provenance, uniqueness/cardinality/audit và workflow link/unlink/recover.
- Không tin X-User-Id/X-Role/X-Patient-Id/X-Service-Name, body actor/doctorId, client phone/email hoặc JWT decode chưa verify. Provider tự xác minh signature/issuer/audience/expiry/session hoặc securely issued opaque credential theo công nghệ của mình **và current state** trước assertion.

## SERVICE và registry

Active SERVICE cũng đúng11fields nhưng principalType=SERVICE; userId/patientId/doctorId **null**; nonempty unique SVC_* authorities. Không human ROLE_* hoặc serviceName wire field.

Auth owner cung cấp exact authenticated issuer-scoped SERVICE sub + allowed authority ceiling. Patient operator cấu hình registry exact-sub→fixed internal serviceName; không tách chuỗi sub hoặc lấy tên service từ caller. Appointment profile lookup cần registered appointment-service + SVC_PATIENT_READ **và real WorkScope APPOINTMENT_PROFILE_READ**. Registry thiếu/vượt ceiling→403. SERVICE không trở thành USER, không self/clinical/search/mutation access.

## Config và secret provisioning

Profile [identity-introspection](../../src/main/resources/application-identity-introspection.yml): PATIENT_AUTH_INTROSPECTION_URI, PATIENT_AUTH_ISSUER, PATIENT_AUTH_AUDIENCE(default patient-service), PATIENT_AUTH_CLIENT_ID, PATIENT_AUTH_CLIENT_SECRET; optional timeout env. DB riêng qua PATIENT_V2_DB_URL/USERNAME/PASSWORD.

Registry và CA-only PKCS12 trust-store/trust-store-password qua protected external Spring config. Không có default allowed subject/token, no trust-all. Giữ secret/signing key/token ngoài Git/docs/command history/APM; tắt HTTP wire/body capture. Rotation/revocation/compatibility window và provisioning authority do owner cung cấp; current consumer dùng config+restart, không giả hot reload.

Disabled/unconfigured identity: missing/malformed bearer401; syntax-valid bearer503. Enabled nhưng invalid/incomplete settings: startup fails closed. Không bật endpoint nhạy cảm để demo.

## Artifacts và acceptance cần Auth owner bàn giao

1. Reviewed repo/ref/commit hoặc real IdP configuration, binary/image/source/config hashes, actual process/container and issuer/audience/endpoint/TLS public cert chain; fresh synthetic identity-store identity. Không chỉ file JSON tự khai REAL_BINARY.
2. Dedicated introspection-client provisioning/least-privilege grants/rotation; external synthetic USER A/B, linked Doctor, unlinked USER, registered and unknown SERVICE; tokens không gửi vào reports.
3. Genuine invalid/expired/wrong-issuer/wrong-audience/revoked/inactive vectors; raw response schema checks; backend bad credential rejected; outages separate from invalid human credential.
4. **Cùng token** active→disable/revoke rồi next request denied, không restart Patient; actual mapping remove/conflict handling và service revoke. Pre-issued denied fixtures hoặc TestAuthServer chuyển active:false không chứng minh live revocation.
5. Actual Patient direct-port self200/cross-patient403/forged-header denial/401/503 và before/after4tables; no unauthorized writes. Consumer tests hiện chỉ COMPONENT/TCP WITH AUTH DOUBLE; default regression counts không phải real Auth results.

Nếu provider muốn dùng draft validateCaller wrapped response hoặc generic OIDC response: gửi versioned diff/request/authentication/error/mapping semantics + rollout/tests; chọn contract decision với nhóm, không yêu cầu Patient tự nhận hai response formats hoặc nới decoder. [Runbook18](18_P_FINAL_REAL_INTEGRATION_RUNBOOK.md) nối provider bằng configuration khi đúng profile, không rewrite Patient domain.
