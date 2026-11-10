# Rà soát logic toàn dự án — 10/11/2026

Phạm vi chốt theo `main`, commit `b2ec0aaf384df7644a703f2e883e4cc1d2f5e7ee`: Auth, Patient, Doctor, Appointment, Notification, Gateway và React. Đọc luồng nghiệp vụ, phân quyền, validation, persistence, retry/idempotency và các hợp đồng giữa service. Không sửa mã nghiệp vụ trong lượt này.

Trong lúc review có thao tác checkout/merge từ bên ngoài. Vì vậy kết quả nghiệm thu cuối dùng bản xuất riêng từ đúng commit trên, build từ đầu, không dùng các class/test còn sót trong `target` của workspace. Các phép tái hiện chỉ dùng mock hoặc PostgreSQL tạm; không ghi vào database đang chạy, không gửi email thật.

**Kết luận:** còn lỗi logic cần sửa dù bộ test hiện có có thể xanh. Ưu tiên identity Auth, API ghi Doctor, tính duy nhất của liên kết hồ sơ và OTP. Onboarding tự động vẫn là điểm chặn nghiệp vụ đã được ghi nhận trước đây.

## Kết quả kiểm tra

Kết quả cuối và lệnh chạy nằm trong `2026-11-10-project-logic-evidence.json` cùng thư mục. Bộ probe được lưu tại [probes](probes/Run-LogicProbes.ps1). Đây là phép tái hiện lỗi, không phải test chứng minh các lỗi đã được sửa.

| Kiểm tra trên snapshot `b2ec0aa` | Kết quả |
| --- | --- |
| Maven toàn bộ reactor | 299 test đạt, 0 lỗi/failure; BUILD SUCCESS |
| React/Vitest | 46 test / 10 file đạt |
| Vite production build | Thành công |
| Hợp đồng giữa service | 13 kiểm tra đạt |
| Probe Auth | Tái hiện sai subject → role, OTP đồng thời và duplicate Patient binding |
| Probe Doctor qua HTTP + PostgreSQL | Tái hiện ghi anonymous, duplicate slot và dữ liệu âm |
| Probe React | Hai invariant hiện đang vi phạm, ghi nhận bằng `it.fails` |

Các số liệu trên thay thế số liệu workspace được thông báo trước khi phát hiện chuyển nhánh. Chưa chạy lại acceptance toàn hệ thống bằng broker/SMTP/browser thật.

## Các lỗi cần sửa

### L01 — P1: Auth có thể gán quyền tài khoản khác khi Keycloak subject không khớp

Nguồn: [UserProvisioningSupport.java:101](../../ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/application/service/UserProvisioningSupport.java#L101), [KeycloakJwtAuthenticationConverter.java:44](../../ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/infrastructure/config/KeycloakJwtAuthenticationConverter.java#L44).

Khi không tìm được tài khoản theo Keycloak ID, `resolveOrSyncUserFromClaims` tiếp tục tra `fclinic_user_id`, email và username rồi trả tài khoản tìm được mà không kiểm tra lại `user.keycloakId == token.sub`. Converter dùng role của tài khoản này để cấp quyền cho request Auth, kể cả API admin. Nhánh introspection chặt hơn không bảo vệ được nhánh converter này.

**Đã tái hiện ở mức service/converter:** claims của subject B có cùng email với tài khoản ADMIN A làm converter trả authority `ROLE_ADMIN`. Đây là phép thử với claims giả lập đầu vào của converter; không phải bằng chứng giả mạo chữ ký JWT. Điều kiện thực tế là có token hợp lệ từ issuer với subject khác và thuộc tính bị trùng, ví dụ sau migration/recreate tài khoản hoặc cấu hình liên kết identity sai.

**Sửa:** nhận diện bằng issuer/subject đã xác minh; từ chối nếu các claim định danh mâu thuẫn. Liên kết/migrate identity phải là thao tác tin cậy riêng, có kiểm tra và audit; không tự nhận diện qua email/username.

### L02 — P1: Tạo bác sĩ và lịch làm việc không cần đăng nhập

Nguồn: [DoctorController.java:55](../../ClinicAppointmentBE/services/doctor-service/src/main/java/com/fclinic/doctorservice/api/controller/DoctorController.java#L55), [DoctorController.java:82](../../ClinicAppointmentBE/services/doctor-service/src/main/java/com/fclinic/doctorservice/api/controller/DoctorController.java#L82).

Các POST public không kiểm tra bearer, vai trò hoặc quyền sở hữu Doctor. Bảo vệ Basic ở `/internal/**` chỉ áp dụng cho reference nội bộ.

**Đã tái hiện bằng HTTP thật trên Doctor + PostgreSQL tạm:** POST tạo bác sĩ và POST tạo slot không có Authorization đều trả 201. Người gọi có thể thêm bác sĩ giả hoặc tạo ca cho Doctor khác.

**Sửa:** xác thực ở Doctor service; tạo bác sĩ theo quyền quản trị, tạo ca theo Doctor-owner hoặc quyền quản lý lịch được cấp. Giữ các GET danh bạ theo chính sách công khai đã thống nhất.

### L03 — P1: Một OTP có thể thực hiện hai lần đặt lại mật khẩu đồng thời

Nguồn: [PasswordRecoveryService.java:83](../../ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/application/service/PasswordRecoveryService.java#L83), [SpringDataJpaPasswordResetOtpRepository.java:11](../../ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/infrastructure/persistence/SpringDataJpaPasswordResetOtpRepository.java#L11).

Hai request có thể cùng đọc `used=false`, cùng kiểm tra OTP thành công, rồi cùng gọi Keycloak đổi mật khẩu. Repository không khóa bản ghi OTP; entity không có version/CAS bảo vệ việc tiêu thụ OTP. `@Transactional` một mình không ngăn được trường hợp này.

**Đã tái hiện bằng hai luồng và hai snapshot đọc độc lập của cùng OTP:** cả hai service call đều hoàn tất và Keycloak port nhận hai lệnh reset với mật khẩu khác nhau. Probe kiểm chứng interleaving ở service, chưa chạy stress qua HTTP với database thật.

**Sửa:** tiêu thụ OTP nguyên tử/serialize cùng OTP và thiết kế phục hồi khi Keycloak lỗi; bảo đảm tối đa một request được quyền đổi mật khẩu. Bổ sung giới hạn số lần nhập sai và tần suất phát OTP: hiện chưa thấy cơ chế này ở service/Gateway được rà soát.

### L04 — P1: Admin có thể gắn nhiều tài khoản vào cùng hồ sơ Patient/Doctor

Nguồn: [AdminUserService.java:106](../../ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/application/service/AdminUserService.java#L106), [JpaUserEntity.java:47](../../ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/infrastructure/entity/JpaUserEntity.java#L47), [V1 auth schema](../../ClinicAppointmentBE/services/auth-service/src/main/resources/db/migration/V1__create_auth_tables.sql).

Các đường provisioning/admin nhận UUID và lưu liên kết; không có kiểm tra tồn tại/ACTIVE/quyền sở hữu hồ sơ hoặc ràng buộc UNIQUE cho `patient_id`/`doctor_id`. Hai tài khoản có thể cùng được cấp quyền self trên một Patient. Ngoài vấn đề riêng tư, recipient resolver từ chối mapping không duy nhất nên outbox enrichment có thể retry kéo dài.

**Đã tái hiện ở service:** hai lệnh update Admin cho hai account cùng `patientId` đều được chấp nhận. Việc thiếu constraint được xác nhận ở entity và SQL; probe này không chứng minh migration của deployment đã được áp dụng.

**Sửa:** kiểm tra hồ sơ bằng hợp đồng tin cậy, ràng buộc duy nhất ở DB cho liên kết không null, xử lý concurrent claims và ánh xạ conflict rõ ràng. Áp dụng cho cả create/update/staff provisioning, không chỉ endpoint self-link.

### L05 — P1: Email nhắc hẹn có thể gửi sau khi lịch đã đổi hoặc hủy

Nguồn: [NotificationDeliveryService.java:39](../../ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/usecase/NotificationDeliveryService.java#L39), [NotificationIngestionService.java:121](../../ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/usecase/NotificationIngestionService.java#L121).

Appointment kiểm tra reminder còn hiện hành trước khi publish. Sau khi Notification nhận và lưu job, nhánh gửi/retry chỉ đọc notification rồi gửi HTML đã render; không đọc `reminder-reference`, không so version/slot/trạng thái hiện tại. Notification job cũng không giữ đủ metadata reminder để đối chiếu.

Tình huống: reminder đã publish → SMTP lỗi hoặc backlog → bệnh nhân hủy/đổi lịch → retry gửi nội dung giờ cũ. Manual retry cũng đi qua cùng đường này.

**Mức bằng chứng:** đối chiếu trực tiếp producer, ingestion và delivery; chưa phát lại chuỗi hủy/đổi → retry trên broker/SMTP thật trong lượt này.

**Sửa:** giữ aggregate version và snapshot định danh slot, kiểm tra reference tin cậy trước mỗi lần gửi reminder, suppress reminder lỗi thời; khi provider không sẵn sàng thì hoãn kiểm tra/gửi. Bổ sung acceptance case qua broker và retry.

### L06 — P2: Doctor cho phép tạo nhiều slot cùng ngày, cùng giờ

Nguồn: [DoctorApplicationService.java:124](../../ClinicAppointmentBE/services/doctor-service/src/main/java/com/fclinic/doctorservice/application/usecase/DoctorApplicationService.java#L124), [Doctor schema](../../ClinicAppointmentBE/services/doctor-service/src/main/resources/db/schema/V001__doctor_uuid_empty.sql).

Service chỉ kiểm tra thời lượng và giờ bắt đầu, sau đó tạo UUID mới. DB có index tra cứu nhưng không có UNIQUE `(doctor_id, slot_date, start_time)`.

**Đã tái hiện bằng HTTP/PostgreSQL thật:** gửi cùng payload hai lần trả 201 với hai UUID khác nhau. Availability có thể hiện hai lựa chọn cho cùng giờ. Appointment vẫn có unique index giờ bác sĩ nên phát hiện này **không** đồng nghĩa hai booking CONFIRMED có thể cùng chiếm giờ đó.

**Sửa:** ràng buộc duy nhất tại DB và trả conflict khi trùng; quyết định cách gộp/dọn dữ liệu trùng hiện có trước migration.

### L07 — P2: Doctor chấp nhận phí khám và số năm kinh nghiệm âm

Nguồn: [CreateDoctorRequest.java](../../ClinicAppointmentBE/services/doctor-service/src/main/java/com/fclinic/doctorservice/api/dto/CreateDoctorRequest.java), [DoctorApplicationService.java:74](../../ClinicAppointmentBE/services/doctor-service/src/main/java/com/fclinic/doctorservice/application/usecase/DoctorApplicationService.java#L74).

Phí chỉ có `@NotNull`; kinh nghiệm và domain không kiểm tra không âm. **Đã tái hiện HTTP 201** với phí `-100`, kinh nghiệm `-2`.

Doctor giả dữ liệu này xuất hiện trong danh bạ nhưng Appointment từ chối fee không hợp lệ ở reference decoder, khiến người dùng chọn bác sĩ rồi không đặt được. Không có bằng chứng booking đã lưu phí âm.

**Sửa:** validation miền giá trị tại DTO và domain, constraint DB thích hợp; phản hồi 400 cho dữ liệu nhập sai. Tiền nên dùng BigDecimal xuyên suốt thay vì Double.

### L08 — P2: Bấm slot trong “Lịch làm việc” của bác sĩ gây lỗi JavaScript

Nguồn: [DoctorDashboardPage.jsx:12](../../ClinicAppointmentFE/src/pages/DoctorDashboardPage.jsx#L12), [DoctorSchedulePage.jsx:470](../../ClinicAppointmentFE/src/pages/DoctorSchedulePage.jsx#L470).

Dashboard render `<DoctorSchedulePage />` không truyền `onSelectSlot`, nhưng các nút slot vẫn gọi callback này. **Đã tái hiện trên component thật:** đi vào tab “Lịch làm việc”, bấm một ca trống phát sinh `onSelectSlot is not a function`.

**Sửa:** thiết kế rõ thao tác của Doctor tại màn hình này: nếu chỉ xem lịch thì bỏ action đặt lịch; nếu có action thì nối handler đúng và giới hạn đúng Doctor. Không dùng callback rỗng để che luồng chưa được xử lý.

### L09 — P2: Lịch bác sĩ hiển thị sai trạng thái trống/đã đặt

Nguồn: [DoctorSchedulePage.jsx:359](../../ClinicAppointmentFE/src/pages/DoctorSchedulePage.jsx#L359), [TimeSlot domain](../../ClinicAppointmentBE/services/doctor-service/src/main/java/com/fclinic/doctorservice/domain/model/TimeSlot.java), [Appointment persistence](../../ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/infrastructure/adapter/JpaAppointmentRepositoryAdapter.java).

Màn hình dùng `slot.isBooked` từ Doctor để đếm “trống” và bật nút. Slot được tạo với `isBooked=false`; luồng booking/cancel/reschedule hiện hành ghi trạng thái chiếm chỗ ở Appointment, không cập nhật cờ Doctor này. Vì vậy slot đã có lịch CONFIRMED vẫn có thể hiện “Chọn khám”.

**Mức bằng chứng:** đối chiếu reader và các writer hiện có; chưa chạy browser acceptance với booking mới trong lượt này.

**Sửa:** kết hợp offer Doctor với occupancy từ Appointment như luồng `SlotGrid` hiện hành. Tránh duy trì thêm một cờ chiếm chỗ độc lập nếu không có cơ chế đồng bộ nhất quán.

### L10 — P2: Hồ sơ onboarding chờ liên kết không được tách theo tài khoản

Nguồn: [PatientOnboardingPage.jsx:21](../../ClinicAppointmentFE/src/pages/PatientOnboardingPage.jsx#L21), [PatientOnboardingPage.jsx:59](../../ClinicAppointmentFE/src/pages/PatientOnboardingPage.jsx#L59).

Cache dùng khóa chung `fclinic.pending.patient`, không gắn account ID, không kiểm tra chủ thể khi đọc lại; async submit không kiểm tra session trước khi ghi cache hoặc gọi link. Dù logout có xóa pending keys, response tạo hồ sơ đến muộn có thể ghi lại dữ liệu A sau khi logout; login B hoặc đổi tài khoản giữa các tab có thể gặp pending cũ.

**Đã tái hiện trên component:** đặt pending của A, render phiên B và submit thì gửi UUID hồ sơ A thay vì tạo/khôi phục hồ sơ B. Backend self-link hiện từ chối nên probe này không chứng minh B chiếm được hồ sơ A; hậu quả hiện tại là retry sai hồ sơ/luồng onboarding mắc kẹt. Không nên bỏ kiểm tra backend để chữa triệu chứng UI.

**Sửa:** cache theo account/subject, kiểm tra phiên trước mọi continuation ghi dữ liệu/link, xử lý response muộn và cleanup sau account switch. Cần idempotency phía server để phục hồi cả khi mất response, reload hoặc dùng nhiều thiết bị.

## Điểm chặn nghiệp vụ đã biết, vẫn còn

**Tài khoản mới chưa tự hoàn tất đường đăng ký → tạo Patient → đặt lịch.** `AuthApplicationService.linkPatientProfile` cố ý trả 403 cho claim mới khi chưa có hợp đồng ownership tin cậy. FE tạo được profile rồi dừng ở provisioning. Đây là chặn bảo vệ quyền đúng, không phải lý do để mở self-link tùy ý. Cần triển khai provisioning tin cậy và idempotency tạo/liên kết; xử lý profile thiếu/inactive và partial failure giữa DB/Keycloak.

`COMPLETED`/`NO_SHOW` có trong enum nhưng chưa có workflow outcome. Tài liệu Appointment hiện chủ ý để ngoài phạm vi; không coi đây là bug hoặc tự thêm chuyển trạng thái khi tạo bệnh án.

Notification dùng JWT offline nên không bảo đảm thu hồi ngay token còn hạn sau logout. Đây là khác biệt chính sách với Patient/Appointment dùng introspection, cần được thống nhất trước nghiệm thu yêu cầu logout toàn hệ thống.

## Những bảo vệ đã có — không ghi lại thành lỗi

- Appointment có kiểm tra self/Doctor-assignment/Staff-scope, version khi hủy/đổi, idempotency bền vững, unique giờ bác sĩ và outbox cùng giao dịch.
- Patient kiểm tra scope, VALID_VISIT, tác giả và Doctor khi sửa bệnh án; phiên bản, revision và khóa Patient khi tạo bệnh án đang được bảo vệ.
- Recipient resolver dùng mapping UUID rõ ràng và từ chối mapping không duy nhất; không tự suy ra account bằng số điện thoại.
- Không tự đánh dấu Appointment hoàn tất chỉ vì có bệnh án.

## Thứ tự xử lý đề nghị

1. L01, L02: chốt identity và phân quyền ghi Doctor.
2. L04 + provisioning: duy nhất/ownership/idempotency cho account–profile, hoàn tất onboarding.
3. L03, L05: OTP dùng một lần; reminder đối chiếu lịch hiện hành khi gửi/retry.
4. L06–L10: constraint/validation Doctor và các lỗi giao diện đã tái hiện.
5. Chạy lại test thường và các test invariant tương ứng sau sửa; acceptance bằng Auth/Doctor/Patient/Appointment/Notification thật cho onboarding, đặt–đổi–hủy và reminder retry.
