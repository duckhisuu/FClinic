# Rà soát bản hiện tại — 16bcd7e

Ngày: 09/11/2026. HEAD: `16bcd7e119078694fec939544157213d02b8e1f3`. Báo cáo này cập nhật trạng thái của review `1399d80`; giữ các báo cáo cũ làm lịch sử. Lượt này kiểm tra source, chạy lại tests và probe, chỉ bổ sung tài liệu; chưa sửa production code, không triển khai hoặc gửi tin nhắn cho thành viên.

**Kết luận:** Doctor đã sửa lỗi persistence gây thất bại startup trên DB mới. Chưa thể kết luận luồng Appointment chạy thông: seed/mapping/slot Doctor, cấu hình Docker và credential, recipient, FE và Notification vẫn còn các điểm chặn. Phát hiện thêm test navigation FE đang fail và bộ OpenAPI/event schema bàn giao chưa theo kịp UUID/schema3.

## Đã sửa — không bàn giao lại như lỗi đang tồn tại

| Mục | Trạng thái đã xác nhận | Giới hạn |
| --- | --- | --- |
| N01 cũ: Doctor UUID/JPA | Đã bỏ `@GeneratedValue` ở cả hai entity. Fresh UUID PostgreSQL + Doctor application thật khởi động được; tạo và save lại Doctor/slot thành công. | Không chứng minh migration từ DB bigint cũ. |
| Port Patient | Default datasource đổi sang `localhost:5443/patient_uuid`; `.env.example` cũng dùng 5443. | Compose không có env vẫn fallback 5433 và tạo `patient_db`; đổi port không tạo schema/database `patient_uuid`. |
| Appointment ↔ Auth | Bearer introspection, self/owner, assigned DOCTOR, scoped STAFF, authorization trước replay vẫn có trong code và regression tests. | Không đưa lỗi “missing Principal thành anonymous” từ docs cũ vào backlog hiện tại. |
| Appointment → Patient | Backend dùng `/api/v3/patients`, role template/registry đã cùng `SVC_PATIENT_READ`, scope provider có implementation. | Còn deployment TLS, subject, secrets, profile và human grants cần provision. |
| Patient → Appointment | Local URI v2, UUID projection, header `/2` và Basic credential riêng đã có. | Cần cấp credential hai phía; Compose Appointment chưa forward nó. |
| Gateway Appointment | Route v2 đã có; v1 vẫn được chuyển đến service để trả 410. | Service phải đăng ký discovery khi đi qua `lb://`. |

## Phần cần xử lý trong Appointment

### A01 — P1: Docker chưa chuyển cấu hình security vào container

Nguồn: [Compose Appointment](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/docker-compose.yml:13>), [cấu hình security](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/resources/application.yml:52>).

Chạy `docker compose config --format json` với env synthetic: cả chín biến kiểm tra gồm introspection URI/secret, scope URI/secret, Patient/Notification reference secret, Patient token URI/secret và issuer đều không được forward. Đặt chúng trong env file của Compose không tự đưa chúng vào process Java. Default Auth localhost bên trong container cũng không trỏ container Auth.

**Appointment + Ops cần:** forward đầy đủ URI/client ID/secret/issuer, cấu hình trust store khi dùng CA riêng, địa chỉ DNS/TLS đúng topology; kiểm cả inbound reference lẫn outbound introspection/scope/token. Nghiệm thu trên container thực bằng bearer hợp lệ và credential riêng của Patient, thêm trường hợp sai credential/outage. Không nới guard để chạy demo.

### A03 — P1: Recipient resolver chưa có provider

Nguồn: [recipient adapter](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/infrastructure/adapter/HttpAppointmentReferenceAdapter.java:175>).

Adapter POST `{patientId,doctorId}` chờ đúng hai recipient PATIENT/DOCTOR, nhưng URL mặc định rỗng; chưa có provider tương ứng trong controller Auth/Patient/Doctor. Booking có thể commit nhưng outbox tiếp tục pending enrichment/retry.

**Appointment phối hợp Auth/recipient owner:** chốt endpoint, version, nguồn account mapping và contact, credential và các trường hợp thiếu/ambiguous mapping. PATIENT chưa liên kết account có thể có `userId=null`; DOCTOR phải có account mapping theo contract hiện tại. Không suy userId từ Doctor.id, phone/email hoặc ID giả. Nghiệm thu booking → enrichment → event thực; broker ACK chưa chứng minh đã gửi email.

### A12 — P1: Credential Doctor/recipient và bảo vệ transport chưa khép

Nguồn: [các lời gọi provider](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/infrastructure/adapter/HttpAppointmentReferenceAdapter.java:73>), [ProviderHttp](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/infrastructure/ProviderHttp.java:35>).

Client-credentials có refresh mới áp dụng cho Patient. Doctor/profile/slot/recipient còn dùng chung `APPOINTMENT_SERVICE_TOKEN` tĩnh, default rỗng; thiếu token bị chặn ngay tại client. Doctor hiện không xác thực token đó, nên bật một token bất kỳ không chứng minh đã nối quyền SERVICE.

ProviderHttp còn cho phép HTTP tới host bất kỳ và đính kèm bearer, trong khi AuthHttpTransport giới hạn HTTP ở loopback. Default Compose gọi Patient/Doctor qua HTTP DNS. Đây là điểm cần xử lý khi khép credential/transport, tránh gửi bearer trên mạng không có TLS.

**Appointment + provider owner cần:** cơ chế xác thực riêng đã thống nhất, audience/authority tối thiểu, vòng đời token/secret, TLS/trust và negative checks tại provider. Quyền machine không thay thế quyền caller.

### N03 còn — P1: Availability nâng slot chưa công bố thành published

Nguồn: [offered adapter](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/infrastructure/adapter/HttpAppointmentReferenceAdapter.java:147>), [Doctor listSlots](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/doctor-service/src/main/java/com/fclinic/doctorservice/application/usecase/DoctorApplicationService.java:97>).

Probe xác nhận Doctor trả slot `published=false`, Appointment gọi `parseSlot(n,true)` và availability hiện `available=true` với slot future 60 phút hợp lệ. Booking đọc internal slot thì từ chối. **Appointment + Doctor** cần thống nhất offered contract và xử lý published nhất quán ở list/availability/booking. Occupancy tiếp tục do Appointment quản lý.

## Việc chuyển cho các owner khác

| Mục / mức | Owner | Lỗi còn lại và tiêu chí xử lý |
| --- | --- | --- |
| N02 — P1 | Doctor + Auth | Cả bốn UUID seed không đạt UUIDv4/RFC4122 của Appointment/Patient. Auth `bs_an` trỏ UUID khác, không có trong seed Doctor. Chốt mapping bền vững và dữ liệu liên quan; directory tải được chưa có nghĩa book được. |
| A02 — P1 | Doctor | Seed và createTimeSlot vẫn cho 30 phút; Appointment yêu cầu bắt đầu đúng giờ và đủ 60 phút. Probe lưu slot 30 phút vào PostgreSQL thật thành công nhưng policy Appointment từ chối. Sửa provider + seed/data theo contract, không bỏ kiểm tra ở consumer. |
| N04 — P1 | Doctor + Gateway/Auth | Internal Doctor endpoint chưa authenticate: trên application thật, cả anonymous và bearer giả đều nhận 200. Gateway route đang mở `/internal/v1/doctors/**`. Public POST Doctor/slot cũng chưa có role/owner guard. Bảo vệ ở service cả direct port và Gateway. |
| A02 reference — P1 | Doctor + Patient | Chưa có Basic SERVICE enforcement/header `patient-doctor-reference/2`; 404 thiếu `code`/`fieldErrors` mà Patient decoder yêu cầu. Probe xác nhận 404 bị coi là dependency unavailable. Hoàn thiện cả success/error/credential contract và bật profile có chủ đích. |
| N05 — P1 nếu dùng DB cũ | Doctor + Ops | SQL mới chỉ tạo bảng UUID khi chưa có; `ddl-auto=update` không phải migration bigint → UUID với mapping. Chuẩn bị backup/clone, mapping PK/FK/consumer, migrate rồi validate. Không khẳng định DB nhóm đã lỗi vì chưa kiểm volume thật. |
| A04 — P1 | Notification | Producer schema3 dùng UUID; DTO còn Long và DB còn BIGINT ở appointment/account. Probe deserialize UUID vẫn thất bại. Chuyển đồng bộ DTO/domain/entity/DB/API/query; bỏ fallback userId 1L/2L và doctorId-as-userId. |
| A08 — P1 | Notification + FE/Auth | Drawer mọi role gọi admin list/retry. Backend chưa owner/admin enforcement; `/me` còn nhận userId Long tự khai. Derive UUID từ trusted principal, owner detail và role cho admin/retry; drawer người dùng gọi /me. |
| A09 — P1 | Notification | Chưa đọc reminder-reference trước SMTP/retry, chưa lưu đủ context để loại nhắc lịch cũ sau cancel/reschedule. Thêm credential riêng, đối chiếu status/version/schedule/due và suppress stale reminder. |
| A10 — P1 | Auth | Source vẫn cho Google email/googleId tự khai → issue token, public register lưu patientId client chọn, default trả OTP. Bỏ identity/mapping chưa xác minh, dùng trusted link và cấu hình recovery phù hợp. Scope/self check ở Appointment không sửa được identity đầu vào sai từ Auth. |
| A05 — P1 | FE | Wizard vẫn GET/search Patient v2; backend v3. DoctorDashboard dùng adapter cũ Appointment v1 → 410 rồi fallback mock; còn filter theo tên và mong array thay vì page.content. Dùng live adapter v2/v3, pagination, lỗi thật và doctorId từ backend. |
| A06 — P1 | FE + Patient/Auth | “Tạo hồ sơ tại quầy” gọi Auth tạo account, có thể patientId=null rồi hiển thị User.id như Patient.id. Email UI optional nhưng DTO account required. Dùng Patient v3 cho hồ sơ; account/link là workflow riêng. Scope tạo hồ sơ và scope booking/đọc cần cấp riêng. |
| A07 — P2 | FE + Patient | PatientModule không có menu và App không truyền session/token/permission display. Nối host session theo contract; không tự cấp quyền lâm sàng hoặc CRUD chỉ để bật nút. |
| A11 — P2 | FE | Pending namespace dùng fullName; walkinPatient và state không reset theo identity. Dùng verified userId/subject, reset state theo account, giữ retry key/body của đúng account. Backend owner guard vẫn đang chặn sai owner. |

## Mới xác nhận trong lượt này

### N06 — P2: Test navigation FE thất bại sau khi đủ dependencies

Nguồn: [navigation.test.jsx](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/patient/__tests__/navigation.test.jsx:11>).

Sau `npm ci`, `npm test -- --run` chạy được: **34 pass, 1 fail**, không còn tình trạng thiếu executable vitest. Test render App khi chưa có phiên Auth rồi đòi thấy DoctorDirectory; thực tế là LoginPage. Test còn giả định menu chung cũ, gồm Patient menu hiện chưa được nối.

**FE cần:** cập nhật test theo authenticated role/session và menu đã chốt, đồng thời xử lý A07. Không chỉ đổi assertion để bỏ qua luồng Patient. Thêm coverage cho các use case Appointment dùng API thật theo contract, role, lỗi và đổi account.

### N07 — P2: OpenAPI/schema/fixture bàn giao vẫn dùng ID số và event v2

Nguồn: [OpenAPI business id](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/docs/integration-readiness/contracts/appointment-business.openapi.json:609>), [event schema](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/docs/integration-readiness/contracts/events/created.event.schema.json:22>), [B02 trong bảng contract](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/docs/integration-readiness/02_PROVIDER_CONSUMER_CONTRACTS.md:7>), [producer thực](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/infrastructure/adapter/OutboxAppointmentEventPublisher.java:39>).

OpenAPI còn khai báo path id là integer/int64; schema event có `schemaVersion.const=2` và appointment/doctor/slot ID số; fixture cũng như vậy. Code đang dùng UUID và schema3. Bảng B02 còn ghi Patient v2. Thành viên làm theo các file này có thể tiếp tục xây consumer sai.

File `09_SECURITY_INTEGRATION.md` đã nói rõ thay thế mô tả security cũ; **không coi các câu anonymous/no-security trong tài liệu lịch sử là bug source hiện tại**. Tuy nhiên chưa có bộ contract/fixture hiện hành đầy đủ thay thế phần ID/event cũ.

**Appointment + consumer owners cần:** phát hành bộ OpenAPI/schema/fixture hiện hành đồng bộ UUID/schema3/Patient v3 và security scheme; phân biệt rõ artifact Report được pin để lưu lịch sử với contract triển khai mới. Không sửa file pin lịch sử để tạo bằng chứng tương thích giả.

### N08 — P2: Session refresh chưa nối với request Appointment khi token hết hạn

Nguồn: [AuthContext init](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/context/AuthContext.jsx:41>), [Appointment response interceptor](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/appointment/api.js:29>).

Kiểm tra source: refresh chỉ ở bước khởi tạo AuthContext nếu getMe lỗi. Khi mở app đủ lâu để access token hết hạn, request Appointment nhận 401; interceptor chỉ trả ApiError dù refresh token có thể còn hiệu lực. Chưa có refresh một lần và đồng bộ giữa các request đang chạy, cũng chưa đưa caller về trạng thái hết phiên ở nhánh này.

**FE/Auth cần:** chốt UX hết phiên/refresh; nếu retry sau refresh, giữ nguyên body, version và Idempotency-Key, không tự tạo lệnh booking mới. Đây là finding từ control flow source, chưa phải browser E2E đã tái hiện.

## Gate môi trường và CI vẫn mở

- Patient `service-integration` bật identity/scope/reference/registry; Doctor reference vẫn cần profile riêng. Identity/scope bắt buộc HTTPS, còn Auth default HTTP. Cần TLS endpoint, trust đúng và URL không trỏ localhost của container khác.
- Subject SERVICE phải khớp realm đang chạy và registry Auth/Patient. Template realm cập nhật không tự migrate realm/volume cũ. Cấp mới token sau khi đổi role/audience.
- Human STAFF/DOCTOR grants mặc định rỗng. Quyền Appointment không tự cho search/đọc/tạo Patient. Việc tạo Patient mới cũng chưa tự tạo assignment cho UUID mới; owner phải chốt workflow cấp quyền nếu cần đặt lịch ngay sau intake.
- Port 5443 đã khớp `.env.example`, nhưng shared Compose vẫn tạo `patient_db` mặc định; Patient validate `patient_uuid`, không auto-init. Phải chuẩn bị DB/schema và trusted account mapping thật.
- Appointment/Patient discovery mặc định tắt trong khi Gateway dùng `lb://`. Cần bật đăng ký và kiểm TLS/discovery metadata theo topology đã chọn.
- CI backend chỉ `compile`/`test`, chưa `verify` nên bỏ Failsafe; CI FE chỉ build, không chạy test. Bổ sung contract/provider/JPA/FE gates có ý nghĩa.

## Bằng chứng kiểm tra tại 16bcd7e

| Kiểm tra | Kết quả | Phạm vi |
| --- | --- | --- |
| Full Maven verify | PASS: **363 tests**, failure/error/skipped = 0 | Gateway 1, Auth 10, Patient 294, Doctor 8, Appointment 29, Notification 21. |
| FE production build | PASS | Không chứng minh API binding hay role flow đúng. |
| FE test | **34 pass / 1 fail**, 5 files | N06; test navigation thất bại, không phải thiếu Vitest. |
| Auth/Patient/Appointment contract checks | **8 PASS** | Producer/decoder/policy thực, IdP/storage mocks. |
| Fresh Doctor + PostgreSQL thật | Startup/create/resave Doctor và slot PASS | Dùng đúng dependency classpath Doctor, chỉ thêm thư viện PostgreSQL tạm; không trộn auto-configuration security từ module khác. |
| HTTP Doctor thật | Anonymous và bearer giả đều **200** ở internal slot | N04; direct TCP, không chỉ standalone MockMvc. |
| Doctor/Appointment/Patient component probes | 8 mismatch checks xác nhận | UUID seed/mapping, hidden slot, availability, missing reference contract/header/error. |
| Notification/intake/slot probes | 4 mismatch checks xác nhận | UUID DTO, email validation, account không có Patient mapping, slot 30 phút. |
| Compose config với env synthetic | **9/9 biến security kiểm tra không được forward** | Không chạy/deploy container, không dùng secrets thật. |

Logs: `%TEMP%/fclinic-current-16bcd7e-backend.log`, `-fe-build.log`, `-fe-test.log`, `-boundaries.log`, `-doctor-runtime.log`, `-doctor-contracts.log`, `-remaining.log`. Probe source trong `%TEMP%`: `DoctorStartup16bcd7eProbe.java`, `DoctorContracts1399Probe.java`, `FClinicRemainingIntegrationProbe.java` (hai probe tên cũ được chạy với classes bản hiện tại). Database probe tạo riêng, đóng sau kiểm tra; không dùng database/realm/volume của nhóm. Chưa nghiệm thu E2E toàn hệ thống với provider thật, TLS/realm, broker và SMTP.

## Thứ tự xử lý và nội dung bàn giao

1. **Auth/Doctor/Notification:** khép identity và authorization tại provider; ưu tiên A10/N04/A08 trước khi dùng dữ liệu thật.
2. **Doctor + Appointment:** UUID mapping, slot đúng giờ/60 phút, published offer nhất quán và Patient reference contract; startup N01 đã đóng.
3. **Appointment + Ops/Auth:** A01/A03/A12 — Compose/TLS/credential và recipient resolver. Đây là ba việc chính còn lại của Appointment, cộng N03 ở consumer.
4. **FE + Patient/Auth:** API v2/v3, walk-in profile/mapping/scope, doctor read-only, session và pending theo account; sửa test đang fail.
5. **Notification:** UUID/schema3 xuyên suốt, recipient đúng, reminder revalidation; smoke event → job → SMTP có delayed queue/cancel/reschedule.
6. **Appointment/docs + CI owner:** xuất contract/schema/fixture hiện hành và đưa các gate kiểm thử vào CI.

**Có thể gửi Doctor:** “Merge 16bcd7e đã hết lỗi startup/JPA trên fresh PostgreSQL; create/resave Doctor/slot đã pass. Còn seed không đạt UUIDv4/không khớp Auth, slot 30 phút, unpublished offer và internal không auth (TCP anonymous/bearer giả đều 200). Nhờ khép các mục này và Patient reference header/404/credential; N01 không cần mở lại.”

**Có thể gửi Auth:** “Appointment/Patient introspection-scope-projection checks vẫn pass. Còn recipient provider/account mapping, trusted account↔Patient linking và provision TLS/subject/human grants. Các nhánh Google tự khai, public patientId và OTP default còn nguyên; nhờ xử lý ở nguồn identity.”

**Có thể gửi FE:** “Wizard Patient v2 và DoctorDashboard Appointment v1/mock còn nguyên; cần v3/v2 + page.content. Walk-in đang tạo Auth account thay vì Patient profile. PatientModule chưa nối session/menu, pending dùng fullName. FE test hiện chạy được: 34 pass, 1 navigation fail; chốt thêm refresh khi token hết hạn.”

**Có thể gửi Notification:** “Producer đang schema3 UUID nhưng consumer/DB còn Long/BIGINT và fallback userId. Cần khép UUID/recipient, owner/admin API và reminder-reference trước SMTP/retry. Dùng contract hiện hành mới thay vì fixture v2 còn nằm trong docs.”
