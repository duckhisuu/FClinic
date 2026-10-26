# Owner handoff requests

Ngày 24/10/2026. Đây là **draft có thể gửi**, chưa gửi cho ai và chưa có owner chấp thuận. Cá nhân phụ trách và backup còn MISSING theo C-OWN:7–15. Các reference S-/X-/C- ở [01 evidence catalogue](01_M5_READINESS_AUDIT.md#evidence-catalogue); hashes/version và mẫu adoption ở [02](02_CONTRACT_ADOPTION_MATRIX.md).

## H-01 — Auth owner, phối hợp Patient/Gateway/Doctor/Appointment

**Decision/boundary:** D-01/04/05/06, B-02/04/05/07; findings F-01/02/06/07/09.

Repository có draft `auth.openapi.json` version1.1-proposed-pack1, `validateCaller` tại POST /api/v1/auth/validate, `CallerContext`. Đây là input đang chờ adoption, không phải hợp đồng/runtime đã được xác nhận.

**Cần bàn giao:**

1. Chấp thuận hoặc thay draft bằng verifier profile có version/hash: chọn verification strategy có thẩm quyền (local verification hoặc agreed validation/introspection), exact wire inputs/outputs/errors. Nếu dùng draft validateCaller, xác nhận no-body/bearer transport và cách service caller được authenticate song song với human token. Nếu local verification, xuất issuer/audience/key-distribution/algorithm/expiry/rotation/current-activation profile; không lấy HS256 design làm lựa chọn mặc định.
2. Signed D-04 permission mapping ROLE_STAFF→receptionist administrative grant, assigned object/search scope; cách revoke/current evaluation, tách clinical.
3. Trusted linking subject/userId↔Patient UUID/Doctor Long: source of truth, verification, cardinality, optional/unlinked account behavior, recovery/orphan/deactivation/reassignment, ai được link existing profile. Không phone/email auto-link.
4. SERVICE identity, credential provisioning và authority cho Appointment→Patient và Patient→Doctor/Appointment; secret reference/access policy/rotation/revocation owner; TLS/backend-caller authentication profile, không gửi credential value trong tài liệu.
5. Mapping service identity vào current internal `TrustedCaller.serviceName`: PatientPolicy yêu cầu appointment-service nhưng closed draft CallerContext chưa có field đó. Cần quyết định nguồn trusted identity/mapping hoặc contract amendment; không thêm field một phía, suy từ browser header hoặc parse opaque subject.
6. Bảng valid/invalid/expired/inactive/missing-mapping/technical outage expected outcomes và fixture account labels trong môi trường test, không token thật trong handoff.

**Câu hỏi bắt buộc:** ai chịu trách nhiệm activation freshness và outage? User/service audience/grants khác nhau ra sao? Cách chặn caller không có quyền gọi validator? Khi mapping thiếu so với malformed provider DTO xử lý403 hay503 ở boundary nào? Scope data do Auth sở hữu hay provider khác? Cách identify machine service đáng tin để kiểm B-02? Ai ký compatibility window?

**Acceptance:** owner+consumer ký artifact hash; chứng minh valid identity và invalid/expired/inactive401; technical verifier503; wrong self/role/scope403; forged headers không cấp quyền; SERVICE không trở thành human/admin; unlinked profile/account không được tự ghép. Evidence không chứa token/secret.

**Evidence cần gửi:** adopted schema/profile + approval record, provider build/version, redacted PT reports, secure credential provisioning reference, sample synthetic context/error fixtures và rotation/revocation test. Nếu Auth module/build ở repo khác, cung cấp reference đó; monorepo hiện không có auth-service module (S-PARENT).

**Patient sẽ làm:** M5-A wire DTO + selected verifier adapter, validate payload rồi map trusted context, giữ interceptor/direct enforcement, typed401/503; M5-B nối scope đúng owner; chạy T-ID/T-WS và direct TCP suite05. Không tự triển khai login/register hoặc sửa Auth service ở task Patient.

## H-02 — Doctor owner

**Decision/boundary:** D-01/05/06/07 và B-04; F-02/08/11/13.

X-DOC đã có GET /api/v1/doctors/{id}, full DoctorResponse chứa id/active. Draft doctor OAS version1.1-proposed-pack1/hash trong02 giữ full DTO, không có endpoint mới cần Patient tự bịa.

**Cần bàn giao:**

- Signed acceptance của getDoctor/path/full wrapped DTO/required/null/error; build provider hỗ trợ. Xác nhận authority cho Patient SERVICE read và backend credential provisioning từ H-01.
- Exact behavior Doctor missing/inactive và schema-invalid/technical401/403/5xx; Patient consumer sẽ phân biệt missing/inactive→422 với technical→503.
- Nếu Doctor sở hữu assignment/valid visit: xuất scope evidence contract/hash, allowed operations/patient binding/currentness/expiry/revocation và linked/walk-in evidence. Nếu không sở hữu, chỉ rõ owner authority thật, không same-clinic grant.
- Agreed endpoint environment/discovery/TLS và bounded validation budget/retry policy do owner/Integration Lead ký; chưa chọn các giá trị proposed.
- Fixture doctors active/inactive/missing/wrong identity; no credentials hoặc data bệnh nhân thật.

**Câu hỏi:** active là field bắt buộc hay có projection khác được ký? Status/profile đổi sau snapshot được xử lý/reconcile bởi ai? Invalid payload phải bị consumer reject thế nào? Ai kiểm credential scope ở direct Doctor port?

**Acceptance:** PT-B-04-getDoctor + CT-B-04-getDoctor trên provider thật, scope denial, missing/inactive semantics và technical503/no Patient write; network calls chứng minh trước PatientWriter transaction.

**Evidence cần gửi:** owner/consumer approval record, hash/schema fixtures, provider build/test reports, environment/service grant reference và timeout policy version.

**Patient sẽ làm:** strict full-wire decoder→DoctorReference(id,active), separately authenticated HTTP adapter, typed failure mapping, request correlation, no-write fault tests. Không chạy fake Doctor server trong production.

## H-03 — Appointment owner

**Decision/boundary:** D-01/04/05/06/07/17/19; B-02 và B-05; F-03/07/08/13.

X-APT hiện nhận Long patientId và client snapshots; bookAppointment gọi slot lock trước khi có human/Patient validation. Current v1detail không đáp ứng B-05 minimum protected v2 projection.

**Cần bàn giao:**

1. B-02 consumer change plan/build: UUID/full canonical PatientResponse decoder, ACTIVE check, server-owned snapshots; không Long cast/fallback hoặc phone identity. Áp dụng sequence human-auth→self/receptionist scope→SERVICE lookup→ACTIVE→hold/write, không dùng machine credential thay caller gốc.
2. B-05 signed minimum medical reference schema: id, patient UUID, doctorId, status; exact route/operationId/authority/errors/required/null/status vocabulary. Draft getAppointmentV2 ở02 là public full DTO; D-19 cho phép owner chọn role-specific projection trên route đó hoặc versioned route đã được ký. Patient không chọn thay.
3. Current caller/Doctor assignment và valid-visit authority cho linked/walk-in (D-07); cách bind attestation vào Patient/Doctor/appointment/visit.
4. D-17 exceptional correction/reconciliation khi cancellation/status đổi sau reference read. MVP chỉ non-CANCELLED + scope/valid visit; không tự đòi COMPLETED hay mutation side effect.
5. D-15 UUID consumer persistence migration readiness và stable mapping consumption do owner tự thực hiện trên owned database.

**Câu hỏi:** exact service authority và service-name mapping? Missing/cancelled/mismatched refs phân biệt với unauthorized machine credential thế nào? Status unknown/missing payload reject503 ở consumer theo schema nào? Original caller deny có đảm bảo không lookup/lock/outbox không? Không có valid visit thì lấy authority từ đâu? Ai chịu reconciliation sau snapshot?

**Acceptance:** PT/CT-B-02 và B-05 trên real providers; patient A→B denied trước SERVICE lookup/slot hold, technical Patient validation fail không booking/history/outbox/idempotency write; active UUID hợp lệ giữ đúng identity. B-05 mismatched/cancelled/missing422, technical503; linked unique409, walk-in không Appointment lookup. No automatic complete/cancel from Patient.

**Evidence cần gửi:** both-owner approval/hash, compatible consumer/provider builds, original-caller ordering trace đã redact, DB/slot before-after assertions, minimal projection fixtures và credential/budget profile references.

**Patient sẽ làm:** provider tests B-02; adopted Appointment wire decoder→internal facts, unknown-status validation trước boolean conversion; adapter/pretransaction/no-write tests B-05. Không sửa Appointment code/DB trong Patient audit.

## H-04 — Gateway owner

**Decision/boundary:** D-01/05/06/21; B-07; F-04/05/09.

X-GW chỉ có Patient v1 route; X-CORS chưa expose X-Request-Id. C-GW:16–18 là target cần verify, chưa có bằng chứng runtime.

**Cần bàn giao:** route config/build cho /api/v2/patients collection root và toàn nested paths; no inappropriate StripPrefix; verifier/current activation integration theo H-01; bỏ spoofable identity headers, forward original bearer. Pin behavior Content-Type/X-Request-Id/error/status/body preservation và CORS expose correlation. Timeout/retry của Gateway không tự retry POST/PUT; broker/idempotency policy Appointment không áp Patient POST.

**Câu hỏi:** how generate/validate/echo requestId giữa incoming/no-header/error responses? Giữ correlation cho401/403/503 trước MVC thế nào? Authority/DNS/service registration profile nào được ký? Compatibility/retire v1 coordinated-only hay window được owner chấp thuận?

**Acceptance:** same request qua direct TCP và Gateway có cùng semantic status/code/data/fieldErrors; collection+detail+medical đủ8operations; invalid/expired/inactive401, outage503, forged identity không grant; no mutation retry; CORS đọc được X-Request-Id. Không quay route v1 vào v2 schema.

**Evidence cần gửi:** config/build hash, approval record, endpoint/certificate/discovery references, redacted request/response assertions và deny tests; không bearer trong log/capture chia sẻ.

**Patient sẽ làm:** correlation filter/context theo agreed policy, resource authorization độc lập Gateway, run paired direct/Gateway matrix. Source Gateway do owner sửa trong task riêng.

## H-05 — Frontend owner

**Decision/boundary:** D-01/04/06/21; B-07, IT-02; F-04/11/12.

X-FE api.js:350–365 vẫn by-phone/upsert và fallback id1; BookingPage:123 fallback1; error interceptor :21–27 làm mất structured code; booking catch :248–301 báo success synthetic. Đây là blockers real acceptance.

**Cần bàn giao:** consumer adoption của8Patient operations/UUID/full DTO/page/null/version/error. Change plan bỏ implicit demo fallback trong real mode, không auto chọn bệnh nhân đầu tiên cùng phone, không Number(patient UUID), patient self read/receptionist assist theo trusted identity. Full PUT gửi required mutable fields + version, optional omitted/null xóa; medical reason/version/ref immutability. Giữ HTTP/code/fieldErrors/requestId để409 reload-review thay auto thay version/retry.

**Câu hỏi:** real/demo mode tách thế nào để không giả success? Error401/403/409/422/503 hiển thị theo code ở flow nào? Lost-response POST được reconcile qua GET/scoped search hoặc medical appointmentId filter như thế nào, khi phone không còn unique? Frontend không bao giờ nhận machine credential chứ?

**Acceptance:** E2E real Auth/Gateway/providers; cùng phone hai UUID chọn đúng; blocked Patient validation không “booking success”; lost-response không blind retry; stale409 giữ unsaved data/review; missing scopes không expose clinical. Không suy từ layout hoặc MockMvc là E2E.

**Evidence cần gửi:** consumer build/lockfile/config version, adopted schema hash và PT/CT/E2E reports với synthetic fixtures; DevTools assertions đã loại token/PHI, correlation labels.

**Patient sẽ làm:** full DTO/known error fixtures/provider acceptance, phối hợp paired request và lost-response flows; không sửa FE ở task audit.

## H-06 — Integration Lead / Operations / từng DB owner

**Decision/boundary:** D-01/15/21, M5 signoff/M6 prerequisites; F-01/09/10/11/14.

**Cần bàn giao:** tên owner/backup có thẩm quyền; per-boundary adoption record02; compatibility/cutover window; giải quyết503 coverage, safe integer hardening, permission summary và chain budget/retry. Kế hoạch environments tách fresh-M5 khỏi legacy clone/M6, credential distribution references và evidence access/redaction policy.

**Câu hỏi:** ai ký mỗi consumer và exclusion? Tool/version migration nào được duyệt? Schema/data/writers inventory, actual old timezone và owner mapping exports ở đâu? Restore consistency across DB và forward-fix authority? Ai xác nhận stop/drain direct writes/jobs/seed, ai cho mở lại ghi? D-17 xử lý external snapshot race ra sao?

**Acceptance:** M5 không dựa mock; release không mở trước M6/signoff nếu legacy data. Checklist06 có accountable owner/evidence/stop condition; backup restore/rerun/UUID bijection/no merge được rehearsal trên clone trước execution authority.

**Evidence cần gửi:** approvals có ngày/version/hash/window, build/consumer compatibility matrix, controlled inventory và restore reports đã redact, môi trường/test-account provisioning references. Không gửi backup dump, credentials hoặc raw PHI vào bộ audit.

**Patient sẽ làm:** triển khai từng stage04 sau gate, thu evidence05; chỉ chuẩn bị M6, không tự migrate shared DB/Auth/Appointment. Các request ở file này chưa được gửi hoặc coi là approved.

