# Đối chiếu logic và security — 09/11/2026

Mã nguồn được kiểm tra: commit `6147053`. Phạm vi chính: Appointment ↔ Patient, Appointment ↔ Auth, và các boundary security của Gateway/Doctor/Notification liên quan. Đây là báo cáo review; chưa thay đổi production code hoặc cấu hình chạy.

Kết luận: logic nghiệp vụ nội bộ của Appointment có nhiều phần phù hợp tài liệu, nhưng chưa đủ điều kiện xác nhận luồng đặt lịch tích hợp và phân quyền toàn hệ thống. Auth có ba nhánh nguy hiểm đã tái hiện ở mức component. Patient có enforcement khá chặt, nhưng các dependency/provisioning cần để Appointment gọi vào chưa khép kín.

## Nguồn đối chiếu và giới hạn

- [Permission matrix và thứ tự authorization](<C:/Fall 2026/MSS301/clinic-appointment-system/Report/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/Authentication_Authorization.md:26>).
- [Quy tắc giao tiếp liên service](<C:/Fall 2026/MSS301/clinic-appointment-system/Report/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/Service_To_Service_Communication.md>).
- [Auth functional specification](<C:/Fall 2026/MSS301/clinic-appointment-system/Report/FClinic_Implementation_Ready_Pack/03_Service_Specifications/Auth_Service/FUNCTIONAL_SPEC.md:13>).
- [Appointment functional specification](<C:/Fall 2026/MSS301/clinic-appointment-system/Report/FClinic_Implementation_Ready_Pack/03_Service_Specifications/Appointment_Service/FUNCTIONAL_SPEC.md:13>).
- [Contract Appointment UUIDv4/Patient v3 hiện hành](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/docs/uuid/README.md>).
- [Patient final gate](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/patient-service/docs/integration-readiness/39_PATIENT_HTTP_UUID_FINAL_GATE.md>).

Report gốc có nhiều contract còn proposed và ID lịch sử. Review giữ các nguyên tắc security/nghiệp vụ của Report, nhưng dùng UUIDv4, Patient v3 và reference `/2` theo handoff hiện hành. Không coi khác biệt với ID số cũ là lỗi. Các chỉ dẫn giới hạn scope trong tài liệu cũ là bối cảnh bàn giao, không phải bằng chứng rằng security đã được triển khai ở nơi khác.

Chưa dùng token thật để khai thác tài khoản, chưa gọi reset mật khẩu thật, chưa thay đổi database/volume đang dùng, và chưa xác nhận deployment thực tế có reverse proxy hoặc cấu hình ngoài repository. Những nhận định về default config được ghi rõ là default.

## Các phát hiện cần ưu tiên

### R01 — P1: Google login có thể cấp phiên cho tài khoản được chọn bằng email chưa xác minh

Vị trí: [AuthApplicationService.java:124](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/application/service/AuthApplicationService.java:124>), [SecurityConfig.java:61](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/infrastructure/config/SecurityConfig.java:61>), [KeycloakRestAdapter.java:514](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/infrastructure/adapter/KeycloakRestAdapter.java:514>).

`/auth/google` là public. Khi thiếu authorizationCode, service nhận email/googleId từ body, tìm tài khoản bằng googleId hoặc email, tự liên kết rồi gọi `issueTokenForFederatedUser`. Nhánh này không kiểm chứng `idToken`, chữ ký Google, issuer, audience hoặc quyền sở hữu email. Không có giới hạn chỉ dành cho tài khoản PATIENT. Adapter còn bắt mọi lỗi token exchange, đổi mật khẩu tài khoản sang mật khẩu ngẫu nhiên rồi password-login.

Tác động: yêu cầu giả có thể nhắm cả tài khoản ADMIN; fallback còn có thể làm mất khả năng đăng nhập bằng mật khẩu cũ. Component probe đã xác nhận input chỉ gồm email/googleId chưa xác minh dẫn tới yêu cầu cấp token cho synthetic ADMIN. Keycloak trong probe là mock; chưa thực hiện takeover trên IdP thật.

Hướng sửa: bỏ nhánh email/googleId tự khai, chỉ tạo phiên sau luồng OIDC được xác minh; bỏ fallback reset mật khẩu; liên kết identity cần quy trình xác thực chủ tài khoản.

### R02 — P1: Default quên mật khẩu trả OTP cho người gọi public

Vị trí: [application.yml:66](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/auth-service/src/main/resources/application.yml:66>), [PasswordRecoveryService.java:52](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/application/service/PasswordRecoveryService.java:52>).

`AUTH_OTP_EXPOSE_IN_RESPONSE` mặc định true, mail mặc định false. `/forgot-password` và `/reset-password` đều public. Người biết email có thể lấy OTP trong response rồi đặt lại mật khẩu nếu chạy với mặc định này. Probe đã xác nhận service trả OTP usable với policy mặc định; không in mã OTP ra bằng chứng và không reset tài khoản thật.

Hướng sửa: mặc định không trả OTP, gửi qua kênh đã xác minh, cấu hình môi trường demo riêng. Bổ sung giới hạn tần suất/lần thử và cơ chế consume OTP nguyên tử; brute-force protection của Keycloak không tự bảo vệ endpoint OTP tự viết này.

### R03 — P1: Public registration cho phép tự chọn liên kết patientId

Vị trí: [RegisterRequest.java:37](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/api/dto/request/RegisterRequest.java:37>), [AuthApplicationService.java:78](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/application/service/AuthApplicationService.java:78>), [UserProvisioningSupport.java:60](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/application/service/UserProvisioningSupport.java:60>).

Body đăng ký nhận patientId, truyền thẳng qua command/provisioning rồi lưu Auth và attributes Keycloak; chỉ xác thực hình dạng UUIDv4. Không kiểm tra hồ sơ tồn tại hoặc bằng chứng người đăng ký sở hữu hồ sơ. Probe đã xác nhận mapping do client chọn được lưu nguyên.

PatientPolicy tin mapping này để cấp self profile/medical access. Khi bật introspection tương thích, tài khoản mới có thể được coi là chủ hồ sơ của người khác. Điều này vi phạm trực tiếp yêu cầu “Không cho patient ... tự gửi mapping để link hồ sơ” trong Auth spec.

Hướng sửa: tài khoản tự đăng ký để unlinked; tạo mapping bằng onboarding/link verification đáng tin cậy. Không dùng trùng điện thoại/email làm chứng cứ liên kết.

### R04 — P1: Appointment không authenticate/authorize caller và không nối Auth

Vị trí: [AppointmentController.java:53](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/api/controller/AppointmentController.java:53>), [AppointmentController.java:101](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/api/controller/AppointmentController.java:101>), [AppointmentController.java:157](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/api/controller/AppointmentController.java:157>), [IdempotentAppointmentOperation.java:26](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/application/usecase/IdempotentAppointmentOperation.java:26>).

Module không có authentication verifier/JWT resource server/role-policy. Principal chỉ được dùng làm subject audit/idempotency; thiếu Principal chuyển thành `anonymous`. GET list/detail/history/reference không nhận trusted caller. Cancel/reschedule chỉ kiểm trạng thái, thời gian và version; không kiểm chủ lịch. List không có filter có thể lấy toàn bộ dữ liệu. Các response chi tiết có snapshot cá nhân và reasonForVisit.

Bearer được FE gửi không tự tạo Java Principal khi service không có bộ xác thực. `APPOINTMENT_SERVICE_TOKEN` chỉ là credential đi ra provider; nó không chứng minh người gọi hiện tại được phép đặt/hủy lịch. Gateway cũng chưa có lớp security bù cho phần này. Khi service được truy cập, caller có thể đọc và thay đổi lịch của người khác; cancel còn không cần Patient provider hoạt động.

Idempotency replay chạy trước authorization vì chưa có authorization; mọi caller không được xác thực dùng cùng namespace anonymous. Audit luôn ghi role SYSTEM. Đây là gap được Appointment README thừa nhận, nhưng vẫn là blocker security so với Report.

Hướng sửa: xác thực original bearer và current activation; thiết lập trusted USER/SERVICE context; authorize self/scoped STAFF/assigned DOCTOR trước replay và trước dùng machine credential. Cả direct port, list và các projection SERVICE phải có enforcement. Không chữa bằng cách tin X-Role/X-User-Id hoặc patientId FE gửi.

### R05 — P1: Service-account authority của Auth/Keycloak không khớp Patient

Vị trí: [realm.json:643](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/infra/keycloak/fclinic-realm.json:643>), [RemoteWorkScopeAdapter.java:47](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/scope/RemoteWorkScopeAdapter.java:47>), [IntrospectionDecoder.java:67](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/identity/IntrospectionDecoder.java:67>).

Seed service-account-appointment-service có `SVC_PATIENT_PROFILE_READ`, còn Patient yêu cầu chính xác `SVC_PATIENT_READ`. Token đúng chữ ký vẫn không vượt được policy lookup. Trước đó còn phải khai báo registry subject → serviceName/authorities trong Patient; registry mặc định rỗng nên SERVICE bị 403.

Hướng sửa: thống nhất một tên authority giữa provisioning, introspection, registry và policy; khai báo đúng verified subject của service account; vẫn giữ resource scope. Không có bằng chứng rằng chỉ đổi APPOINTMENT_SERVICE_TOKEN là đủ.

### R06 — P1: Patient Auth/scope chưa có đường cấu hình default chạy xuyên suốt

Vị trí: [PatientConfiguration.java:19](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/config/PatientConfiguration.java:19>), [PatientConfiguration.java:34](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/config/PatientConfiguration.java:34>), [IdentitySettings.java:34](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/identity/IdentitySettings.java:34>), [application-scope-authority.yml](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/patient-service/src/main/resources/application-scope-authority.yml>).

Identity default disabled: bearer hợp cú pháp nhận 503. Scope default deny; ngay cả SERVICE đúng danh tính cũng chưa được phép lookup hồ sơ. Bật identity/scope đòi endpoint HTTPS, credentials và authority provider; Auth mặc định phục vụ HTTP và chưa có controller cung cấp administrative/clinical scope. Introspection tương thích `/2` đã có ở Auth, nhưng nó không thay scope authority.

Đây là integration gap, không phải lỗi cần sửa bằng cách mở quyền Patient. Cần cấu hình TLS/proxy phù hợp, bật profile, cung cấp scope authority thật và provisioning đầy đủ. Login Auth thành công không đồng nghĩa Patient API hoạt động. Tests hiện có dùng upstream doubles.

### R07 — P1: Patient → Appointment dùng sai collection URI default; credential chưa được enforce phía provider

Vị trí: [application-local.yml:7](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/patient-service/src/main/resources/application-local.yml:7>), [ReferenceHttpClient.java:24](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/ReferenceHttpClient.java:24>), [AppointmentRequestIdFilter.java:26](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/api/AppointmentRequestIdFilter.java:26>).

Local default trỏ `/internal/v1/appointments`; client nối `/{id}/medical-reference`. Appointment hiện chỉ cung cấp `/api/v2/appointments/{id}/medical-reference`. URI cũ không có route/header contract, nên consumer chuyển thành DEPENDENCY_UNAVAILABLE, làm linked medical create thất bại sau khi các gate trước đã được cấu hình.

Sửa URI sang v2 sẽ khớp UUIDv4, bốn trường projection và header `patient-appointment-medical-reference/2`. Header này hiện đã được implement, không phải phần thiếu. Tuy nhiên Patient gửi Basic client credential còn Appointment bỏ qua nó vì thiếu security; response 200 không chứng minh machine authorization. Cần thống nhất và enforce credential trước nghiệm thu.

### R08 — P1: Gateway chưa route Appointment v2

Vị trí: [Gateway application.yml:26](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/infra/api-gateway/src/main/resources/application.yml:26>), [Appointment application.yml:54](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/resources/application.yml:54>).

Gateway chỉ match `/api/v1/appointments/**`; BE và FE hiện gọi v2. V2 không có route; v1 được Appointment trả 410. Dev Vite proxy đi thẳng service nên có thể che lỗi Gateway. Ngoài ra Gateway dùng `lb://`, trong khi Auth/Patient/Appointment mặc định `EUREKA_ENABLED=false`: cần explicit discovery config hoặc routing topology tương ứng mới resolve được.

Hướng sửa: route đúng version và cấu hình service discovery nhất quán; kiểm chứng qua Gateway ngoài dev proxy. Đây là thay đổi routing, không thay thế authorization R04.

### R09 — P1: FE đặt lịch vẫn gọi Patient v2

Vị trí: [appointment/api.js:81](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/appointment/api.js:81>), [PatientController.java:14](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/api/controller/PatientController.java:14>).

Appointment patientApi search/get dùng `/api/v2/patients`, nhưng Patient hiện chỉ map v3. Vite proxy không rewrite path. Do đó ngay cả user đã liên kết patientId vẫn không tải được hồ sơ cho wizard. Sau khi sửa route, user PATIENT chỉ được GET self, không được search; user unlinked không nên rơi vào UI search toàn bộ bệnh nhân. Luồng search dành cho người có quyền/scope tương ứng.

### R10 — P1: Doctor provider hiện hành chưa khớp UUID/slot mà Appointment yêu cầu

Vị trí: [DoctorController.java:48](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/doctor-service/src/main/java/com/fclinic/doctorservice/api/controller/DoctorController.java:48>), [HttpAppointmentReferenceAdapter.java:111](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/infrastructure/adapter/HttpAppointmentReferenceAdapter.java:111>).

Doctor public controller dùng Long cho doctorId. Appointment yêu cầu UUIDv4 doctorId/slotId và GET `/internal/v1/doctors/{doctorId}/slots/{slotId}` có published boolean; chưa thấy provider route tương ứng trong main source Doctor. Vì vậy dù khép Patient/Auth, real booking vẫn bị chặn ở Doctor. Không nên ép Appointment đổi contract về Long hoặc bỏ published validation để che mismatch. Cần Doctor provider và migration/mapping đồng bộ theo contract được nhóm chấp thuận.

### R11 — P1: Notification API chưa có owner/admin enforcement

Vị trí: [NotificationController.java:33](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/api/controller/NotificationController.java:33>).

`/notifications/me` nhận userId tùy ý qua query, detail không kiểm owner, admin list/retry không kiểm ADMIN. Module chưa có authentication/security policy và Gateway vẫn route các endpoint này. Caller có thể đọc metadata người khác hoặc yêu cầu retry khi có kết nối tới hệ thống. UUID khó đoán hay tên `/me` không tạo ra quyền sở hữu.

Hướng sửa: derive recipient userId từ trusted principal; áp owner/admin policy ở service và direct port; chuyển định danh theo Auth contract hiện hành.

### R12 — P2: Appointment chưa có recipient provider tương ứng trong Auth

Vị trí: [HttpAppointmentReferenceAdapter.java:176](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/infrastructure/adapter/HttpAppointmentReferenceAdapter.java:176>), [Appointment application.yml:47](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/resources/application.yml:47>).

Outbox enrichment cần POST patientId/doctorId, trả đúng hai recipient. URL default rỗng và chưa có endpoint tương ứng trong Auth controllers. Booking có thể commit nhưng event ở PENDING_ENRICHMENT/retry, chưa thể kết luận đã gửi notification. Đây là dependency D-08 còn mở, đã được docs thừa nhận.

Hướng sửa: thống nhất provider sở hữu mapping/account/email, route, DTO và SERVICE authority; không suy tài khoản từ phone/email hoặc tạo recipient giả.

### R13 — P1: Notification chưa consume được event UUID schemaVersion 3

Vị trí: [AppointmentNotificationEvent.java:35](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/dto/event/AppointmentNotificationEvent.java:35>), [AppointmentNotificationEvent.java:99](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/dto/event/AppointmentNotificationEvent.java:99>), [RecipientDto.java:10](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/dto/RecipientDto.java:10>).

Appointment phát UUIDv4 cho appointmentId/doctorId/slotId/userId. Notification DTO/domain vẫn dùng Long cho các ID này nên payload mới không deserialize đúng. Consumer còn fallback userId null thành 1L/2L, trái yêu cầu không phát minh tài khoản nhận. Cần migrate consumer/persistence/API và có contract test schemaVersion3; giữ userId null cho người chưa có account theo chính sách được chốt.

### R14 — P2: Auth có fallback ghép identity bằng email/username mà không kiểm subject khớp

Vị trí: [UserProvisioningSupport.java:101](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/application/service/UserProvisioningSupport.java:101>).

Khi không tìm thấy keycloakId/fclinicUserId, code tìm bằng email hoặc username rồi trả user hiện có, không xác nhận keycloakId của user trùng subject đã xác minh. Trong trường hợp registry lệch/orphan hoặc tái sử dụng identity, một subject khác có thể nhận role và mapping của bản ghi cũ. Điều kiện khai thác phụ thuộc dữ liệu IdP; đây là kết luận đọc source, chưa tái hiện với realm thật. Hướng sửa: binding ổn định issuer+subject; linking/recovery explicit, không silent fallback bằng email.

## Các phần phù hợp đã kiểm tra

- Appointment đọc Patient v3 và snapshot tên/điện thoại từ provider, kiểm ACTIVE; không tin tên/phí từ browser. PatientResponse hiện có đủ 14 trường mà adapter yêu cầu.
- Medical-reference Appointment và decoder Patient đã khớp bốn trường `id,patientId,doctorId,status`, UUIDv4 và reference contract `/2` khi cấu hình đúng URI. Không nhầm header contract với authentication.
- Appointment tạo CONFIRMED; hủy/đổi chỉ CONFIRMED tương lai, kiểm version; đổi lịch giữ patient/doctor/fee snapshot.
- Partial unique CONFIRMED theo doctor/date/start bảo vệ occupancy độc lập slotId; transaction ghi appointment/history/result/outbox được thiết kế cùng unit of work. Chưa chạy lại toàn bộ concurrency/outbox harness của Appointment trong lượt review này.
- Patient có self/role/scope/author policy, fail closed khi identity/scope thiếu; medical create đối chiếu appointment patient/doctor và không chấp nhận CANCELLED. Lookup từ xa tách khỏi write transaction, recheck ACTIVE và audit atomic trong writer được regression kiểm tra.
- ROLE_ADMIN không mặc nhiên được cấp clinical access ở Patient. Identity header tự khai không được dùng làm trusted caller. Không nên nới các điều kiện này để làm demo chạy.

## Kiểm chứng đã chạy trong lượt review

1. Maven `-pl services/auth-service,services/patient-service,services/appointment-service -am test`: BUILD SUCCESS. Patient 213 unit tests, F/E/S=0. Auth và Appointment báo **No tests to run** qua Maven; kết quả build này không chứng minh security của hai module.
2. Maven `-pl services/patient-service -am verify`: BUILD SUCCESS, 213 unit + 80 integration = **293 tests**, F/E/S=0. Integration dùng PostgreSQL thật được harness tạo riêng, upstream Auth/scope/reference là doubles, chưa phải real-service E2E.
3. Java component probe gọi production AuthApplicationService/PasswordRecoveryService/UserProvisioningSupport với repository và Keycloak ports là Mockito mocks. Đã tái hiện R01/R02/R03 bằng dữ liệu synthetic; không có network tới IdP hoặc database thật. Probe ở `%TEMP%/fclinic-logic-review-20261109/AuthLogicProbe.java`, log cùng thư mục `probe-results.log`.

Log Maven: `%TEMP%/fclinic-logic-review-tests-20261109.log` và `%TEMP%/fclinic-logic-review-verify-20261109.log`. Dùng Maven bundled của IntelliJ và JDK 21 vì `mvn` chưa nằm trong PATH. Không chạy lại Python harness Appointment cũ vì fixture còn Patient v2/ID số, chưa phản ánh contract UUID hiện hành. Không khẳng định full reactor/notification runtime hoặc toàn hệ thống pass.

## Thứ tự xử lý đề xuất

1. Khép R01/R02/R03, thêm regression phủ các nhánh bypass và linking; rà R14.
2. Thiết lập trusted caller và object/scope authorization ở Appointment, các projection SERVICE và Notification; bảo vệ cả direct port. Authorization phải chạy trước idempotency replay.
3. Chốt authority/provisioning/registry, TLS và scope provider cho Patient; không chuyển fail-closed thành allow-all.
4. Đồng bộ URI Patient→Appointment, FE Patient v3, Gateway Appointment v2 và discovery; kiểm chứng cả Gateway lẫn direct port.
5. Khép Doctor UUID/published-slot, recipient provider và Notification UUID/schema3.
6. Chạy E2E bằng provider thật: self booking/read/cancel/reschedule, cross-patient denied, scoped STAFF, DOCTOR assigned reads, inactive/expired token, SERVICE misuse, idempotent replay sau thu hồi quyền, linked medical record và notification đúng người nhận.
