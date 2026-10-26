# M5 implementation plan — conditional, không triển khai ở audit

Ngày 24/10/2026. Nguồn: [01 findings/evidence](01_M5_READINESS_AUDIT.md), [02 adoption matrix](02_CONTRACT_ADOPTION_MATRIX.md), [03 owner inputs](03_OWNER_HANDOFF_REQUESTS.md), [05 executable test matrix](05_M5_TEST_MATRIX.md).

**Scope lượt này: chỉ tài liệu và regression đã có.** Mọi class/config/test path trong phần “FUTURE” là đề xuất để task lập trình tiếp theo tạo sau gate, chưa tồn tại và không là approved wire contract. Giữ nguyên Q-01..05 và core transactions. Không đổi shared modules trong task Patient.

## Việc làm ngay và điểm phải dừng

| Có thể chuẩn bị ngay | Bắt buộc chờ adoption / quyền execution riêng |
|---|---|
| Giao draft H-01..06 cho người dùng/owner, xác định người ký, pin hashes và exclusions | Không tự gửi messages; không coi draft đã được chấp thuận |
| Review test cases/additional component tests được liệt kê05; chuẩn bị synthetic accounts theo registry | Chưa thêm tests trong audit này; test doubles chỉ test source, không chứng minh provider |
| Review correlation/error translation design F-05/06 và schema conformance assertions | Production/test code không sửa ở audit; correlation header invalid policy/rollout cần owner chốt |
| Chuẩn bị per-boundary acceptance record, environment inventory read-only | Không gọi guessed endpoint, lấy secret, start real integration hoặc migration khi authority/contract thiếu |
| Theo dõi source/evidence hash và giữ regression | Không clean/reset/rebase/commit/push hoặc thay canonical để làm tests xanh |

## Adapter design contracts

Căn cứ các interface S-PORTS/S-DPORT/S-APORT; conditional fallback S-CONF:17–37. `TrustedCaller`, `DoctorReference`, `AppointmentReference` là internal facts, **không là DTO HTTP/JWT**.

### C1 — IdentityVerifierPort

- Input hiện tại: một bearer token opaque, sau interceptor kiểm đúng một Authorization header và scheme. Output: TrustedCaller(subject, principalType, authorities, patientId, doctorId, serviceName, active). Không chứa userId/valid như draft Auth CallerContext.
- Thiếu: H-01 signed verification strategy, issuer/audience/key/expiry hoặc exact adopted validation route, current activation/linking, backend caller auth, USER/SERVICE grant và trusted serviceName provenance.
- FUTURE layout dưới `infrastructure/adapter/identity/`: `AdoptedIdentityVerifierAdapter`, selected strategy transport/verifier, separate wire DTO/decoder và mapping. Không tạo cả local-JWT/introspection implementations rồi chọn một default chưa được ký.
- Validate wire valid/active/type/required/null/IDs/authorities và payload semantics trước map context. USER phải có mapping theo use case; SERVICE không tự có human role/patient/doctor mapping. Không deserialize closed CallerContext thẳng vào internal record; không thêm serviceName một phía.
- Invalid/expired/inactive credential→typed401; technical verifier outage/5xx/malformed payload/failed technical caller auth→typed503; local wrong-self/permission→403 trong PatientPolicy. Forged headers không phải input của adapter.
- DTO/credential/current activation handling cần unit vectors và real provider CT/PT; direct TCP401/503 phải chạy với actual adapter. Runtime clock/network/key caching chỉ theo signed policy, không tự chọn revocation SLA.

### C2 — WorkScopePort

- Input: verified caller + resource UUID; validVisit còn appointmentId nullable/visitDate; output: boolean grants hoặc SearchScope(Set<UUID>).
- Source of truth **chưa có**: H-01/H-02/H-03 phải xác nhận admin assignment, clinical assignment và valid-visit authority độc lập. Không ép các quyền này cùng một backend nếu owner chưa chốt.
- FUTURE `infrastructure/adapter/scope/AdoptedWorkScopeAdapter` + wire DTOs/decoders theo authority được ký. Search null = không grant→403; authorized set rỗng = page rỗng/count0, không search toàn DB. Không construct SearchScope từ null hoặc chứa null; malformed provider response→503 trước constructor.
- Network/credential/schema/outage **không được biến thành null/false**: throw typed DEPENDENCY_UNAVAILABLE để S-ERR trả503 (F-06). Boolean false chỉ biểu diễn deny hợp lệ.
- Receptionist admin create/object/search tách clinical; role/same-clinic không grant. Evaluate current scope mỗi read/create/PUT, kể cả no-op. validVisit của walk-in vẫn bắt buộc, null appointment chỉ bỏ Appointment lookup.
- Policy hiện kiểm một số scope cả controller và application (S-API:25/31/41/48/58/70; S-APP). Không tự cache grant làm mất freshness hoặc thay ordering; any request-bound memoization cần freshness authority và tests riêng.
- Evidence có thể cần bind principal/patient/doctor/appointment/visit/operation và currentness; exact fields/transport/signature/expiry do owner cung cấp, không tự định nghĩa attestation schema.

### C3 — DoctorClientPort

- Input: validated positive safe Long doctorId; output Optional<DoctorReference(id,active)>. Canonical candidate là C-DOC getDoctor/full wrapped Doctor DTO, chưa adopted.
- FUTURE `infrastructure/adapter/reference/AdoptedDoctorClientAdapter` + `DoctorWireResponse`/strict decoder + service credential source. Chỉ lấy id/active **sau** full adopted wire validation; primitive active không được default false từ field thiếu.
- Adopted provider not-found→Optional.empty→Patient422; active=false→Patient422; wrong returned resource id/malformed required payload→503. Network timeout/provider5xx/outgoing credential401/403→503, không human401. Other statuses chỉ map theo signed error profile, chưa có thì fail closed/protocol error, không fake active.
- Không gọi trong PatientWriter/local write tx; không tự retry/budget, không cache active=true vô thời hạn. Endpoint base/discovery và TLS do environment owner cung cấp; không nhận base URL từ request.

### C4 — AppointmentClientPort

- Input: positive safe Long appointmentId, chỉ khi linked. Output hiện tại Optional<AppointmentReference(id,patient UUID,doctorId,cancelled)>; status vocabulary không có trong boolean nên decoder phải validate **trước conversion**.
- FUTURE `infrastructure/adapter/reference/AdoptedAppointmentClientAdapter` + agreed SERVICE wire DTO/decoder. Exact route/projection/authority thuộc D-19; draft public full AppointmentResponse không mặc nhiên là selected wire contract.
- Missing reference→Optional.empty→Patient422; valid cancelled hoặc valid patient/doctor mismatch→Patient422; missing/unknown status/wrong response id/UUID type/invalid physician id/schema→503; transport/5xx/technical credentials→503.
- Non-CANCELLED baseline không có nghĩa chỉ CONFIRMED hoặc phải COMPLETED. valid visit do scope authority, không tự suy từ appointment date, không thêm Appointment ACTIVE field. Parent Patient ACTIVE được recheck **local** trong writer.
- Không state-changing call đến Appointment, không complete/cancel/book từ Patient. Remote snapshot có race sau lookup (C-SVC:10/D-17), owner quyết định reconciliation; không hứa distributed atomicity.
- Walk-in vẫn Doctor/scope/valid-visit; không invoke Appointment. Linked unique constraint và writer rollback vẫn giữ nguyên.

### C5 — Quy tắc chung cho cả bốn adapter

| Khía cạnh | Thiết kế có điều kiện / kiểm tra bắt buộc |
|---|---|
| DI | Add actual bean implementing port trong Patient module; verify một selected bean và fallback không bị dùng nhầm. S-CONF fallback vẫn deny khi chưa cấu hình; không permitAll hoặc test-only bean trong main |
| Configuration | FUTURE typed properties/configuration dưới config; keys/baseURL/strategy/budgets/profile version lấy từ owner record, không đặt default endpoint/claims/scope/timeout/retry values. Config classes là local design, chưa wire authority |
| Secrets | Secret references/injection do Ops/Auth; không inline credential/key/token trong YAML, source, logs hoặc handoff. Rotation/revocation test theo adopted policy; không forward browser SERVICE secret |
| Transport | Separate outgoing machine credential và incoming original human authorization; không re-use machine identity để cấp quyền cho người dùng; Auth validation backend caller auth cần H-01 quyết định |
| Error | Validate HTTP/status/wrapper/schema/semantic IDs trước facts mapping. Typed business422 vs technical503; preserve local human401/403. Không broad catch thành success/active/default ID |
| Correlation | FUTURE request correlation filter/context trước MVC để echo cho401/403/503; propagate agreed X-Request-Id qua clients; invalid/missing header behavior và CORS theo signed C-GW policy, không thêm error-body fields vào OAS một phía |
| Telemetry/privacy | Allowlist requestId/operation/result/status/duration/decisionId, entity IDs khi policy cho phép; không body/full phone/clinical/note/raw audit/Authorization/credential/subject mapping dump. Clear request/MDC context finally, kiểm thread reuse/async; sampling/retention do owner chốt |
| Budget/retry | Required adopted total-chain budget + hop policy, bounded cancellation; không dùng1s/2s/5s proposed hay8s frontend làm selected backend values. Không automatic Patient POST/PUT retry; GET retry chỉ khi signed policy cho |
| Tests | Unit/decoder/doubles → real PT/CT → direct TCP → Gateway → FE E2E; record layer rõ. Controlled fixtures, fault injection/no-write/DB assertions, no token in reports |
| Rollout | Isolated fresh-v2 test environment trước; pin builds/profile/contract hash, credentials và evidence. Disable unfinished adapter/rollback config về last known v2 fail closed; không v1 fallback hoặc DB rollback một phía |

## M5-A — Identity integration

- **Inputs:** H-01 signed verifier/current activation/linking/service identity profile + D-01 record, environment/credential refs; C-PAT/C-AUTH accepted discrepancy list.
- **Gate:** D-04/05/06 và serviceName provenance F-07 resolved cho boundary được bật; exact backend-caller authorization được ký. Chưa đạt → BLOCKED.
- **Exact scope:** implement C1 only selected strategy, wire decoder/context mapper, typed errors; giữ interceptor + PatientPolicy, không thay business M0–M4. Correlation filter có thể cùng task sau agreed header policy.
- **Files likely affected:** FUTURE identity adapter/wire DTO/config/test classes trong Patient; existing IdentityVerifierPort/TrustedCaller/PatientConfiguration chỉ thay nếu owner-approved mapping đòi hỏi, có compatibility/tests; PatientAuthentication giữ fail closed. Không Auth/Gateway source ở task Patient.
- **Tests:** T-ID-01..06/T-ID-ADD (05), existing58 regression, actual Auth provider contract; direct TCP missing/invalid/expired/inactive/outage. Không invent JWT fixture runtime.
- **Exit:** selected bean/config/profile hashed; real valid/deny/outage evidence; no fake serviceName/userId mapping hoặc token leakage; original human và SERVICE phân biệt.
- **Rollback:** dừng bật boundary, last known v2 config/build và deny fallback; preserve DB; revoke/restore credentials theo Ops. Không git reset/revert trong audit.
- **Owner:** Patient implement; Auth accountable identity/credential/mapping; Integration Lead acceptance; QA verify.

## M5-B — Scope integration

- **Inputs:** H-01/02/03 adopted admin/clinical/valid-visit authority profiles, scope freshness/revocation/failure semantics; identity M5-A verified.
- **Gate:** D-04/07 + relevant D-06 resolved; null/empty/outage semantics signed; không dựa role alone.
- **Exact scope:** C2 adapter(s), strict evidence binding/current scope, typed503 technical failures, preserve author/parent/no-op checks; add scope-outage tests vì F-06 chưa được existing suite cover.
- **Files likely affected:** FUTURE scope adapter/decoder/config/tests; existing WorkScopePort/PatientPolicy only for necessary reviewed integration changes, không mở quyền hoặc đổi author policy.
- **Tests:** T-WS-01..04, T-CL-01..03; null grant403, authorized empty search200/count0, real revoke/current-scope check kể cả no-op, no-write and no clinical leak.
- **Exit:** proof grant source thật cho linked và walk-in, wrong self/scope/author deny; unavailable503, không convert network lỗi thành deny giả hoặc full list.
- **Rollback:** withdraw selected scope adapter/profile to deny; dừng clinical traffic có kiểm soát; giữ persisted records/audit.
- **Owner:** Patient implement; authority owner được H-01/02/03 chỉ định; Auth/Doctor/Appointment/business owner sign.

## M5-C — Doctor/Appointment adapters

- **Inputs:** H-02 B-04 và H-03 B-05 signed schemas/routes/status/error, SERVICE credentials/authority, complete budgets/TLS/discovery; M5-A/B ready.
- **Gate:** D-05/07/19 + per-boundary D-01; F-08 unknown/missing payload policy fixed; D-17 exceptional exclusions ghi rõ, không tự mở workflow.
- **Exact scope:** C3/C4 HTTP decoders/adapters, reference/technical distinction, correlation and agreed budgets; S-APP lookup trước writer, walk-in skips only Appointment; không service mutation.
- **Files likely affected:** FUTURE reference/transport/credential config/DTO/tests trong Patient; DoctorClientPort/AppointmentClientPort signatures giữ facts nếu đủ, không tự mở wire projection mới.
- **Tests:** T-REF-01..06/T-MED-01..04, CT-B-04/05 on real providers; assert transaction inactive khi network, success/missing/mismatch/technical/no-write.
- **Exit:** real provider schema/auth/reference checks và DB invariants pass; no unknown status default/non-idempotent retry.
- **Rollback:** withdraw clients →503/deny, halt medical creates; no change to Appointment state or foreign databases.
- **Owner:** Patient adapters; Doctor/Appointment provider builds/contracts; Auth/Ops credentials; QA evidence.

## M5-D — Provider/consumer contract verification

- **Inputs:** pinned adopted Patient/Doctor/Appointment/Auth schemas + synthetic fixtures; compatible consumer builds; D-21 approved amendments/exclusions.
- **Gate:** F-03 Appointment consumer UUID/original-caller-before-lock readiness, H-03 done; D-21 503/safe-range/permission summary resolved hoặc explicitly scoped signoff exclusions chưa release.
- **Exact scope:** PT/CT B-02/04/05/07, full schema validation beyond existing helper; all8 Patient operations success/error/null/bounds, consumers reject malformed dependencies. No changes canonical by Patient unilaterally.
- **Files likely affected:** FUTURE Patient provider/consumer contract tests, approved fixture bindings and test config; other owners change their own consumer/provider source in separate tasks.
- **Tests:** T-CT-01..04 and negative vectors05; booking A→B stops trước SERVICE lookup/hold/write; Patient validation failure no booking/history/outbox/result.
- **Exit:** actual provider+consumer tests/builds/hash match, accepted compatibility, unresolved exclusions visible; mock tests không đủ.
- **Rollback:** keep boundary closed/incompatible build out of deployment; no dual raw/wrapped or Long adaptation.
- **Owner:** Each provider + every consumer sign; Integration Lead records; QA independent evidence.

## M5-E — Direct TCP và Gateway integration

- **Inputs:** all selected adapters/profiles/builds, fresh isolated v2 schema/environment, H-04 route/security/correlation/CORS build, safe test accounts.
- **Gate:** M5-D passed, actual credentials/environment authority; Gateway owner tự sửa route/filter. Không use shared legacy DB để “thử”.
- **Exact scope:** start real Patient TCP + actual Auth/Doctor/Appointment; pair direct and Gateway same operations/errors; root/nested/no StripPrefix/header propagation; identity spoof deny, no mutation retry.
- **Files likely affected:** FUTURE Patient TCP/integration tests/runbook/evidence; correlation config; Gateway source ngoài task Patient, user/owner triển khai riêng.
- **Tests:** T-TCP-01..02, T-GW-01..03; T-ID/WS/ref negatives repeated at transport layer; requestId/error equality, DB before-after, redacted logs.
- **Exit:** network-level evidence có build/profile/hash/requestId/expected-actual, direct không bypass, Gateway không đổi error; không MockMvc-as-TCP.
- **Rollback:** close route/deployment traffic to last approved v2; disable incomplete boundary fail closed; không trỏ v1 vào target schema.
- **Owner:** Patient + Gateway + Auth/Ops; QA; Integration Lead validates environment.

## M5-F — E2E verification và signoff

- **Inputs:** H-05 compatible real FE build/config + M5-E network proof; D-01 signed boundary records; scoped data fixtures.
- **Gate:** implicit fallback/id1 removed from real FE mode by owner; typed409/error and no-blind-retry flow; M6 execution vẫn chưa approved.
- **Exact scope:** IT-02, IT-10, IT-11 with actual participants; same-phone selection, self/staff booking authorization, clinical author/scope, lost-response reconciliation/no-leak.
- **Files likely affected:** Patient evidence/runbook và FUTURE E2E harness nếu assigned; Frontend/Appointment changes do respective owners, không sửa từ Patient audit.
- **Tests:** T-E2E-01..03, T-PRIV-01, T-OBS-01; state/count/version/revision assertions và critical failures demonstrate no-write.
- **Exit:** owners sign M5 INTEGRATION VERIFIED cho exact environment/build/hash/exclusions; không đồng nghĩa RELEASE READY hoặc full IT-12/benchmark completed.
- **Rollback:** freeze signoff/traffic, preserve evidence/data, triage owner-specific defect; no fake success, no v1 restoration.
- **Owner:** Patient/Frontend/Gateway/Appointment/Auth/Doctor + QA; Integration Lead final M5 record; Ops owns later M6 gates.

## Copyable task đầu tiên sau gate

“Dựa vào H-01 adoption record đã ký và hashes trong02, implement selected IdentityVerifierPort adapter dưới Patient module, validate agreed wire payload rồi map TrustedCaller (bao gồm approved service identity provenance). Giữ fail-closed interceptor/policy, typed401/503 và request-bound correlation theo approved header policy. Không sửa Auth/canonical/shared modules; thêm decoder/provider/directTCP tests T-ID-01..06, giữ existing58 regression; dừng nếu profile/credential/mapping evidence thiếu. Bàn giao code/build/profile hashes và redacted actual evidence.”

Câu trên là task có điều kiện; chưa được thực thi và không thể thay H-01 còn MISSING.

