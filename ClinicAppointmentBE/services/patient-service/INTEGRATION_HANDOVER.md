# Patient v2 integration handover

## M5-D2 latest handoff — 24/10/2026

[Actual Appointment/B-05 verification and B-02 limit](docs/integration-readiness/14_M5_D2_APPOINTMENT_PROVIDER_VERIFICATION.md), [evidence](evidence/m5-d2-appointment-provider-verification.json). B-05 selected profile now implemented/verified against the real isolated Appointment binary, preserving pinned schema b2ce460335e273e227b45e982c6cefb2512d2625f6f7ff05f2342d29e34c1e86. B-04 remains verified. No whole-chain adoption or release approval is implied.

Next: provision real Auth USER/service identities and current Patient WorkScope grants for Appointment SERVICE lookup; supply trusted Doctor offered-slot authority and delegated booking policy. B-02 self authorization/ACTIVE ordering and no-write/rollback/concurrency are verified only with test authorities. Public booking remains disabled; durable idempotency/replay, event-v2 adoption and operational Redis/broker validation remain gates. Patient active/identity/slot snapshots do not provide cross-database atomicity; POST must not be retried blindly after lost response.

Appointment default v1 route/writer/seed retired; UUID target requires fresh schema and coordinated M6 data ownership/mapping/drain/restore plan. Target outbox uses UUID APPOINTMENT_BOOKED_V2 and excludes reasonForVisit; relay remains off. Gateway and Frontend must not assume this task supplies their UUID/security/E2E adoption. None of these handoff requests has been sent to another owner by this task.

M5-D1 update24/10: [actual Doctor/B-04 verification](docs/integration-readiness/13_M5_D1_DOCTOR_PROVIDER_VERIFICATION.md) đã có protected Doctor executable JAR, dedicated Basic/HTTPS/constant marker/domain404/strict DTO và actual Patient adapter6vectors PASS. Doctor59tests, Patient254normal/255opt-in,F/E/S0; isolated SELECT-only-role DB/no writes/process cleanup verified. Shared SERVICE bearer adoption không tự đổi. Frontend legacy get/list/slots không compatible reference-only mode; cần consumer coordination riêng, không forward machine secret cho browser. M5-D2 tiếp theo: Appointment own UUID/status/minimum projection và B-02 original-human+ACTIVE validation trước side effects. Các update M5-D/C/B/A bên dưới là historical snapshots.
M5-C update24/10: [reference implementation và provider handoff](docs/integration-readiness/09_M5_C_REFERENCE_IMPLEMENTATION.md) pin PAT-REF-DOC-001 full Doctor profile và PAT-REF-APT-001 minimum Appointment UUID projection. Both production ports CODE COMPLETE/COMPONENT VERIFIED/TCP VERIFIED WITH STUB; provider/whole integration NOT VERIFIED. Doctor actual successful DTO shape tương đồng nhưng thiếu backend protection/version/domain404; Appointment legacy Long/full v1 chưa tương thích selected minimum projection. M5-D cần provider compatible builds/credentials/negative-auth/error/status/UUID evidence; không coi stub pass là shared adoption. Historical updates bên dưới giữ nguyên.

M5-B update24/10: [scope handoff PAT-SCOPE-001](docs/integration-readiness/08_M5_B_SCOPE_IMPLEMENTATION.md) có production WorkScopePort tách administrative/clinical authority, local v1 schema, credentials/config và current assignment/encounter requirements. CODE COMPLETE/COMPONENT VERIFIED/TCP VERIFIED WITH STUB; provider thật chưa verify, không shared adoption. B-04/B-05 M5-C còn cần reference wire/credentials/strict422vs503; scope grant không thay Appointment facts. Historical M5-A/23-10 change list dưới đây được giữ nguyên.

M5-A update24/10: production identity adapter và local wire contract v1 đã PROJECT_DECIDED/implement; component và direct TCP với Auth test double đã verify. [Handoff identity mới](docs/integration-readiness/07_M5_A_IDENTITY_IMPLEMENTATION.md#8-provider-rollout-và-m5-b-tiếp-theo) ghi exact provider profile/config/credential/mapping cần triển khai; chưa có provider thật hoặc shared adoption evidence. Nội dung23/10 dưới đây giữ làm historical boundary change list; không dùng “missing approval” để chặn quyết định kỹ thuật Patient đã được người dùng ủy quyền.

Ngày 23/10/2026. Đây là change list để phối hợp, chưa phải shared contract adoption. Chưa gửi tin nhắn hoặc sửa repository/database của các owner khác.

## Ranh giới cần bàn giao

| Boundary | Provider → Consumer | Việc cần làm trước real integration |
|---|---|---|
| B-02 | Patient → Appointment | Consumer nhận Patient UUID string/full canonical DTO; trusted SERVICE credential/scope được adopt; check original human caller trước lookup, ACTIVE check; bỏ Long/fallback identity |
| B-04 | Doctor → Patient | Agree active Doctor lookup wire DTO/errors/authority/credential/budget; Patient map missing/inactive422, technical failure503 |
| B-05 | Appointment → Patient | D-19 minimum SERVICE projection đủ id/patient UUID/doctorId/status; D-05 service credential; không tự thêm endpoint/projection |
| B-07 | Patient → Frontend qua Gateway | 8 v2 ops, wrapped full DTO/page, strict unknown/null/version semantics, typed error flow; không by-phone single/upsert, không Patient POST retry tự động |

## Việc của từng owner

Auth: D-04/05/06. Chốt ROLE_STAFF→receptionist permission + scope, verifier/current activation/service provisioning, trusted linking subject/userId↔patient UUID/doctorId. Không Patient tự tạo account/link theo phone hoặc email. Context nội bộ có subject/type/roles/mappings, không khẳng định đó là JWT claim names.

Doctor/Appointment/Auth: D-07. Cung cấp nguồn trusted clinical assignment/valid visit cho linked và walk-in. WorkScopePort mặc định deny. Doctor same-clinic hoặc role đơn thuần không đủ.

Appointment: D-19 minimum medical reference projection; Patient đọc trước local transaction. Patient không complete/cancel Appointment khi tạo summary. D-17 exceptional correction/completed/no-show vẫn ngoài MVP.

Gateway: route /api/v2/patients cả collection root và nested; không StripPrefix; giữ Authorization original human, Content-Type, X-Request-Id và status/error/body. Bỏ browser identity headers; direct Patient port vẫn authenticate/authorize. Không route Patient v1 sang schema mới hoặc thêm mutation retry.

Frontend: Patient UUID từ response, không id1/Long/phone-as-identity. Hiển thị nhiều người dùng chung phone, chọn chính xác người; Patient self read only, receptionist assist đúng scope. Khi PUT gửi full mutable required fields + version, optional omitted/null xóa. Handle409 bằng reload/review, không tự thay version rồi retry. Medical PUT cần changeReason, không refs mutation. Không dùng clinical/admin override UI ngoài contract.

Integration Lead/DB owners: D-01 adoption theo hash và D-15 tool/cutover/restore. Appointment/Auth tự backfill owned DB theo stable mapping từ Patient, không cross-DB migration qua Patient.

## Artefact chuẩn cần pin

Pack: docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack

| Artefact | SHA-256 |
|---|---|
| 02_Shared_Contracts/openapi/patient.openapi.json | 63e2e306a2b14d89397bb998be43f8de7b982eadf8f3b88353bc952684ebeb71 |
| 07_Testing_And_QA/fixtures/registry.json | 64d6b73b655638365417bde67b70300295a01c1c1a9d22f230d8d266af33bb67 |
| Patient empty schema SQL, bản copy giữ nguyên nguồn | 1500fb0c94d9a504237889201ce900185c771bc80403d484a1d9d47384536ef6 |

Mỗi adoption cần provider/consumer owner thật, version/hash, ngày, compatibility window, unresolved exclusions và approval evidence. Không dùng bảng này như chữ ký của owner.

## Kiểm thử cần chạy với provider thật

- PT/CT B-02, B-04, B-05, B-07 với adopted artefact và credentials.
- IT-02 profile/self/UUID qua direct+Gateway; IT-10/11 clinical/scope/reference/concurrency; IT-12 clone migration/legacy retirement.
- Invalid/expired/inactive token401; verifier outage503; wrong self/role/scope/author403; wrong nested parent404; no PHI trong error/body/log sai quyền.
- Patient dependency lỗi không được tạo booking; incoming original caller bị từ chối trước SERVICE lookup/hold slot tại Appointment.
- Capture build/source hash, contract hash, requestId, HTTP expected/actual, DB count/version/revision assertions đã redact.

Hiện chỉ có component tests cho Patient trên PostgreSQL thật và doubles cho các provider; các IT liên service ở trên chưa chạy.

## Contract discrepancies cần chốt

1. D-21/503: Patient approved OAS không liệt kê503 ở mọi operation; shared Auth unavailability yêu cầu503. Giữ runtime fail closed, cần owner cập nhật/adopt contract trước full compliance.
2. Permission matrix/handovers dùng dòng tóm tắt chung nhiều operation; chỉ quyền cụ thể trong Patient OAS/SECURITY_RULES được thực thi, receptionist không có clinical access.
3. Safe JSON integer policy là shared hardening; input external business IDs hiện kiểm positive safe range theo data standards, approved OAS gốc vẫn giữ nguyên; cần record D-21 adoption.
4. Timeout/retry budgets trong handover vẫn proposed; chưa cấu hình HTTP adapter thật bằng giá trị tự suy đoán.

## Bước tiếp theo

Chốt D-04/05/06 → cung cấp identity adapter; chốt D-07/19 và B-04/05 → cung cấp scope/reference adapters; chạy real PT/CT và IT-02/10/11; sau đó coordinated D-15 rehearsal/cutover/IT-12. Core và test source đã sẵn để nối adapters, chưa đủ điều kiện release toàn service.


## M5-D provider handoff — 24/10/2026

[Exact Doctor/Appointment/B-02 tasks](docs/integration-readiness/11_PROVIDER_COMPATIBILITY_HANDOFF.md) có source anchors, selected Basic-vs-shared-bearer exclusion, UUID/PENDING ownership và acceptance vectors. [Read-only real-provider runbook](docs/integration-readiness/12_REAL_INTEGRATION_RUNBOOK.md) dùng actual provider JAR/deployment evidence/HTTPS/independent credentials, không DB mutation. Current real B-04/B-05/B-02 BLOCKED/NOT EXECUTED; profile guard đã fail preflight, zero external calls;254normal regressionPASS,54TCP vẫn doubles. Không coi harness hoặc response component tests là provider verified; historical sections phía trên giữ nguyên.
