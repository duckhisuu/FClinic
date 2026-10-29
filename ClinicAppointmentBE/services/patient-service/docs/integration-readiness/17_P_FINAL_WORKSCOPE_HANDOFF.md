# P-FINAL — handoff cho administrative và clinical scope authority owners

26/10/2026. **PATIENT WORKSCOPE CONSUMER READY / REAL AUTHORITY PENDING.** Integration Lead phải phân công owner/source-of-truth cho hai logical boundaries; chúng có thể cùng deployment nhưng không cùng credential/grant. Patient không xây provider/grant table hoặc suy quyền từ role. Yêu cầu chuẩn bị, chưa gửi/được owner adopt.

[Schema](contracts/patient-work-scope-v1.schema.json): `patient-work-scope/1`, SHA-256 **`e402cf523fe69f2fcf35eacb3517ebebc27d3f44653fdad4ab8ac52e7e919de1`**. PROJECT_DECIDED. Source [ScopeQuery](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/scope/ScopeQuery.java), [decoder](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/scope/ScopeDecisionDecoder.java), [adapter](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/scope/RemoteWorkScopeAdapter.java), [policy](../../src/main/java/com/fclinic/patientservice/application/security/PatientPolicy.java). Full hashes/test results: [evidence](../../evidence/patient-p-final-verification.json).

## Authority và operations

| Owner cần phân công | Current authoritative facts | Exact operations |
|---|---|---|
| Administrative assignment/access owner | Receptionist assignment/revocation, patient-object admin grants, authorized searchable UUID membership; approved machine profile-lookup delegation | ADMIN_ASSIGNMENT_CREATE/READ/UPDATE/SEARCH, ADMIN_PROFILE_READ/UPDATE, ADMIN_SEARCH, APPOINTMENT_PROFILE_READ |
| Clinical assignment/encounter owner | Trusted subject↔Doctor link, Doctor↔Patient relationship, current assignment and operation-specific grant, genuine linked/walk-in encounter | DOCTOR_PROFILE_READ, DOCTOR_SEARCH, MEDICAL_READ/CREATE/UPDATE, VALID_VISIT |

ROLE_STAFF chỉ là prerequisite, không receptionist grant. ROLE_DOCTOR/same-clinic không valid relationship/visit. ADMIN/STAFF không clinical override. Valid Appointment reference/status không thay scope; scope grant không thay Doctor/Appointment reference. NO_SHOW không chứng minh encounter. Walk-in appointmentId=null vẫn bắt buộc valid visit evidence.

## Exact wire / trust

POST exact deployment-configured HTTPS URI, application/json; backend Basic từng component form-encoded trước Base64; separate administrative and clinical client IDs/secrets, khác introspection/reference credentials. TLS chain/hostname validation, no redirect/application retry/grant cache. Backend client phải được phép đúng authority/operations và trusted caller-assertion delegation; browser không gọi bằng machine secret.

Closed request: `contractVersion,nonce,requestedAt,authority,operation,caller,patientId,appointmentId,visitDate`. Caller gồm đúng `subject,principalType,patientId,doctorId,serviceName`, lấy từ verified Auth context/registry. Không chứa bearer, phone/email/profile/clinical text. Nonce UUIDv4 mới mỗi query; instants UTCZ. Assignment/search có patientId=null; appointmentId/visitDate chỉ phục vụ VALID_VISIT (nullable appointment cho walk-in).

Owner phải pin issuer/subject namespace được Patient client phép assert, resolve linking/assignment/encounter từ store trusted hoặc delegation đã thống nhất; **request echo không tự tạo grant**. Không dùng caller.role từ browser; role cũng không nằm trong wire request. Issuer/mapping realm đổi phải reprovision và retest.

Closed response: `contractVersion,request,evaluatedAt,allowed,patientIds,evidence`. request phải echo **exact full request**, bind nonce/authority/operation/principal/resource/doctor/appointment/visit. evaluatedAtUTC nằm từ requestedAt−1s đến consumerClock.now+1s. Đồng bộ clock là prerequisite.

- allowed=false: patientIds=null,evidence=null →403. Không dùng denied shape cho dependency outage.
- allowed=true: evidence đủ `assignmentId,active,validFrom,validUntil,visitId`, interval from<until. active=false/future/expired hợp lệ→business403. Missing/wrong type/impossible evidence→503.
- VALID_VISIT: visitId nonblank có provenance; provider kiểm encounter thuộc đúng Doctor/Patient/linked appointment/visitDate. Non-visit operation: visitId=null.
- Search granted: unique lowercase UUIDv4 patientIds array, có thể **rỗng**. Null port scope/no grant→403; granted empty→200/content[]/total0; bounded set→filter/count trong set. Nonsearch patientIds=null. No wildcard/truncation/grant-all.
- Technical401/403/backend failure, timeout/5xx/invalid/misbound/stale/replayed payload→503 DEPENDENCY_UNAVAILABLE; không false/null/empty disguise. No PHI/secret/provider URL in public error.

Current defaults per authority: connect1s/full exchange3s; response256KiB; concurrency32; maxSearchPatients1000 (bounds1..10000). Configurable implementation budgets, không shared operational SLO. Search exceeding configured bounds fails503, phải giải quyết provisioning/chunk contract trước mở rộng; không bỏ scope filter.

## Configuration và rollout

Explicit [scope-authority profile](../../src/main/resources/application-scope-authority.yml): PATIENT_ADMIN_SCOPE_URI/CLIENT_ID/CLIENT_SECRET và PATIENT_CLINICAL_SCOPE_URI/CLIENT_ID/CLIENT_SECRET; optional per-authority CA truststore/limits external protected config. Startup reject credential reuse với Auth và giữa hai authorities; enabled incomplete settings fail closed. Disabled/default port denies403, không grant demo.

Mỗi query mới và controller/application recheck trước writer giúp current revocation; lookup bị reject nếu local write transaction active. Không hứa distributed atomic revocation sau decision cuối cùng; không đặt network I/O trong local write transaction để che race.

## Acceptance trước real authority verification

Owner bàn giao actual source/build/runtime/TLS/store provenance, exact endpoints/authenticated grants/clock policy, synthetic assignments/encounters/denials và revoke workflow. Chạy admin create/object/search(empty/bounded/denied), clinical read/create/update, wrong operation/principal/patient/doctor/parent, valid/revoked/expired assignments, linked và walk-in visit, SERVICE-only profile grant, technical failures và no-write4tables.

Existing [PatientScopeTcpIT](../../src/test/java/com/fclinic/patientservice/scope/PatientScopeTcpIT.java)19cases + decoder/transport/config/policy suites dùng production adapters, fresh PostgreSQL, **AUTH/SCOPE DOUBLES**. Chúng kiểm revoked no-op/mutation, current recheck, scope503, no unauthorized writes, independent service grant. Không chứng minh real assignment store, same-token Auth revocation hoặc real visit. Real Auth và real scope phải ghi từng boundary riêng ở [runbook18](18_P_FINAL_REAL_INTEGRATION_RUNBOOK.md).

Nếu source-of-truth hoặc contract khác schema v1: cung cấp versioned diff + ownership/rollout/test decision; giữ fail closed cho tới tương thích. Không tạo quyền trong Patient DB hay kéo trực tiếp database của service khác.
