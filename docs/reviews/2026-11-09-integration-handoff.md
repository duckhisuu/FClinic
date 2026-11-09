# Rà soát tích hợp sau đợt sửa security — 09/11/2026

Kết luận: phần Appointment/Auth/Patient vừa sửa đã có regression và contract checks, nhưng toàn hệ thống vẫn chưa ghép thông. Các blocker chính còn ở Docker wiring, Doctor UUID/published-slot, nguồn recipient, Notification schema3, FE API binding và onboarding tài khoản ↔ hồ sơ.

Lượt này chỉ review và thêm báo cáo; không tự sửa tiếp production code hoặc gửi thông báo cho thành viên. Ownership được ghi theo service vì tài liệu nhóm chưa gán cá nhân. Nếu bạn giữ Appointment, ưu tiên A01/A03/A12 và phối hợp các owner bên dưới.

## Kết quả kiểm chứng mới

- Full backend `mvn -f ClinicAppointmentBE/pom.xml verify -B -ntp`: **BUILD SUCCESS**, 355 tests pass: Gateway 1, Auth 10, Patient 294, Appointment 29, Notification 21; Doctor không có test chạy trong reactor. Không có failure/error/skipped. Đây không phải real-provider E2E.
- FE `npm run build`: **PASS**. FE `npm test`: **chưa chạy được**, local installation thiếu executable vitest. Không kết luận test FE fail do logic; cần hoàn chỉnh dependencies trước khi chạy.
- `docker compose ... config --format json` với env synthetic: 7 biến auth/scope/token/reference/issuer mới đều không được chuyển vào container Appointment.
- Component probe trên source hiện tại: Notification DTO từ chối UUID appointmentId của schema3; DTO staff-account từ chối email thiếu; staff onboarding tạo Auth user có patientId null; slot 30 phút không đạt policy Appointment. Không gọi IdP/database thật trong các probe này.
- Tái chạy probe Auth cũ: Google login tin email/googleId chưa xác minh, register lưu patientId do client chọn và default recovery trả OTP vẫn tái hiện. Các dependency trong probe là mocks, không khai thác tài khoản thật.

Log: `%TEMP%/fclinic-integration-audit-backend.log`, `fclinic-integration-audit-fe-build.log`, `fclinic-integration-audit-fe-tests.log`, `fclinic-remaining-integration-probe.log`, `fclinic-auth-risk-recheck.log`.

## Bảng giao việc

| ID | Ưu tiên | Chủ trì | Phối hợp | Chặn luồng |
| --- | --- | --- | --- | --- |
| A01 | P1 | Appointment | Ops/Auth | Container không nhận cấu hình security mới |
| A02 | P1 | Doctor | Appointment/Patient/Auth | Chọn giờ, đặt lịch, clinical reference |
| A03 | P1 | Owner recipient cần chốt, đề xuất Auth | Appointment/Patient/Doctor | Outbox enrichment và notification |
| A04 | P1 | Notification | Appointment/Auth | Event UUID schema3 không consume được |
| A05 | P1 | FE | Appointment/Patient | Wizard Patient v2; dashboard Appointment v1/mock |
| A06 | P1 | FE + Auth + Patient | Appointment | Tiếp nhận bệnh nhân/tài khoản không cùng workflow |
| A07 | P1 | FE/Patient UI | Auth/Patient | Module Patient thiếu session và menu truy cập |
| A08 | P1 | Notification + FE | Auth | Notification đọc/retry sai quyền và sai ID account |
| A09 | P1 | Notification | Appointment | Nhắc lịch cũ sau cancel/reschedule/queue delay |
| A10 | P1 | Auth | FE/Patient/Appointment | Nguồn identity/mapping còn đường bypass |
| A11 | P2 | FE Appointment | Auth | State/pending request lẫn giữa các phiên |
| A12 | P2, cần chốt trước protected B03/D08 | Appointment | Doctor/recipient owner | Credential provider ngoài Patient chưa khép |
| G01 | Gate môi trường | Ops/Integration lead | Tất cả | TLS, registry, permission, discovery, DB/data |
| G02 | P2 | QA/CI | Tất cả | CI bỏ qua integration test/FE test |

## Chi tiết và điều kiện nghiệm thu

### A01 — Docker Compose Appointment bỏ sót cấu hình mới

Nguồn: [Compose environment](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/docker-compose.yml:11>), [Dockerfile](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/Dockerfile:4>), [cấu hình security](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/resources/application.yml:44>).

Compose chỉ chuyển các biến cũ. `APPOINTMENT_AUTH_INTROSPECTION_URI`, `APPOINTMENT_AUTH_CLIENT_SECRET`, `APPOINTMENT_SCOPE_URI`, `APPOINTMENT_SCOPE_CLIENT_SECRET`, `APPOINTMENT_PATIENT_TOKEN_URI`, `PATIENT_APPOINTMENT_REFERENCE_CLIENT_SECRET`, `KEYCLOAK_ISSUER_URI` không có trong environment của container dù đã có trong env file dùng để render Compose. Dockerfile chỉ copy JAR, không copy/mount `.env`. File env để Compose thay thế biến không tự trở thành env_file của service.

Hậu quả: bearer thiếu vẫn 401 đúng, nhưng bearer hợp cú pháp đi vào introspection với URL localhost/secret rỗng nên 503; Basic reference cũng không dùng được do secret rỗng. `localhost:8085` trong container trỏ chính container Appointment.

Việc cần làm của Appointment: forward đủ biến/credential mới hoặc dùng env_file có phạm vi phù hợp; cấu hình URL Auth/Keycloak thật, issuer, trust anchors, credential reference, Eureka. Cập nhật env mẫu riêng của Appointment. Auth transport chỉ cho HTTP loopback nên không dùng `http://auth-service` như một cách né TLS.

Nghiệm thu: render Compose chỉ in tên biến/flag có mặt, không in secrets; smoke authenticated read/mutation và Patient reference qua container. Sai secret phải deny; Auth down phải 503. Không reset volume để thử.

### A02 — Doctor provider chưa khớp hai consumer

Nguồn: [DoctorController](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/doctor-service/src/main/java/com/fclinic/doctorservice/api/controller/DoctorController.java:48>), [seed slot 30 phút](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/doctor-service/src/main/java/com/fclinic/doctorservice/application/usecase/DoctorApplicationService.java:214>), [Appointment B03 client](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/infrastructure/adapter/HttpAppointmentReferenceAdapter.java:73>), [Patient Doctor profile `/2`](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/ReferenceHttpClient.java:24>).

Doctor API/domain/JPA hiện dùng Long. Appointment dùng UUIDv4; Auth doctorId cũng là UUID. Thiếu `GET /internal/v1/doctors/{doctorId}/slots/{slotId}` với published boolean. Seed Doctor tạo slot 30 phút, trong khi Appointment yêu cầu bắt đầu đúng giờ và dài 60 phút. Patient doctor-reference còn yêu cầu UUID, Basic credential và response header `patient-doctor-reference/2`; Doctor hiện không đáp ứng protected profile đó. Doctor POST tạo bác sĩ/slot cũng chưa có role/owner enforcement.

Việc cần Doctor bàn giao: ID/mapping UUID đồng nhất với Auth, full profile, published-slot và offered-slots đúng contract, quyền SERVICE/DOCTOR/ADMIN rõ ràng. Không cần gọi ngược Appointment để tính occupancy; Appointment sở hữu confirmed occupancy.

Nghiệm thu: hai consumer thật đọc được cùng UUID; slot future đúng bác sĩ/60 phút book được; unpublished/wrong doctor/expired bị từ chối; sai credential bị chặn. Không ép Appointment về Long hoặc bỏ kiểm tra published/60 phút để che mismatch.

### A03 — Chưa có provider resolve recipient

Nguồn: [recipient adapter](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/infrastructure/adapter/HttpAppointmentReferenceAdapter.java:180>), [recipient-url](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/resources/application.yml:63>).

Appointment cần POST `{patientId,doctorId}` để nhận envelope có data là hai recipient PATIENT/DOCTOR với `{recipientType,userId,name,email}`. Chưa thấy endpoint provider tương ứng trong Auth/Doctor/Patient main controllers; URL mặc định rỗng. Booking vẫn commit được nhưng outbox giữ PENDING_ENRICHMENT/retry, chưa phát email.

Việc phối hợp: chốt owner/URL/credential và nguồn metadata; Auth registry cung cấp userId/mapping, phối hợp Patient/Doctor khi cần. PATIENT có thể unlinked nên userId null theo contract; DOCTOR cần account mapping. Không dò account bằng số điện thoại/email và không tạo userId giả.

Nghiệm thu: booking thật → recipient thật → payload schema3 → broker ACK → consumer tạo đúng job. Lỗi provider giữ intent để retry, không báo đã gửi email.

### A04 — Notification DTO/DB vẫn là ID số

Nguồn: [event DTO](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/dto/event/AppointmentNotificationEvent.java:35>), [fallback tài khoản](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/dto/event/AppointmentNotificationEvent.java:99>), [DDL](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/notification-service/src/main/resources/db/migration/V1__create_notification_tables.sql:4>), [producer schema3](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/infrastructure/adapter/OutboxAppointmentEventPublisher.java:39>).

Producer phát UUIDv4 appointmentId/doctorId/slotId/userId với schemaVersion3. Consumer còn Long; DB appointment_id/recipient_user_id là BIGINT. Probe xác nhận UUID appointmentId bị từ chối khi deserialize. Code còn tự thay userId thiếu bằng 1L/2L và coi doctorId như userId ở một số nhánh.

Việc cần Notification sửa: migrate contract/DTO/domain/entity/DB/query/API đồng bộ UUID; xử lý schema version rõ ràng; giữ null account đúng nghĩa, không phát minh recipient. Cần migration/data mapping có owner khi DB đã tồn tại.

Nghiệm thu: dùng payload thực tế của cả created/cancelled/rescheduled/reminder schema3; kiểm eventId dedup, null recipient account/email theo policy, đúng user account và DLQ khi payload sai.

### A05 — FE đang dùng hai bộ API, version và error behavior khác nhau

Nguồn: [wizard Patient API](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/appointment/api.js:81>), [dashboard bác sĩ](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/pages/DoctorDashboardPage.jsx:13>), [Appointment v1/fallback](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/services/api.js:343>).

Wizard gọi Patient v2, backend chỉ có v3; Vite proxy không rewrite path. DoctorDashboard lại import services/api và gọi Appointment v1 (backend trả 410), sau đó rơi vào mockAppointments. Nó còn filter theo tên bác sĩ, trong khi quyền thật dựa doctorId. Chỉ đổi URL chưa đủ vì v2 list trả page/content thay vì array trần.

Việc FE sửa: dùng một adapter live đúng `/api/v2/appointments` và `/api/v3/patients`, xử lý pagination, giữ bearer/idempotency/version và hiển thị lỗi thật. Chỉ search Patient cho caller có quyền; tài khoản PATIENT unlinked cần onboarding, không cho search toàn bộ. Mocks chỉ ở preview rõ ràng; không catch 401/403/410/503 rồi hiển thị thành công/dữ liệu giả.

Nghiệm thu: login PATIENT có mapping → chọn giờ → tải hồ sơ v3 → booking v2 → đọc/đổi/hủy; DOCTOR chỉ thấy lịch của doctorId mình; backend tắt phải hiện lỗi.

### A06 — “Tạo hồ sơ tại quầy” thực chất đang gọi API tạo account

Nguồn: [form FE](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/pages/StaffWalkinPage.jsx:51>), [Auth staff service](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/application/service/StaffPatientAccountService.java:44>), [DTO email bắt buộc](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/api/dto/request/StaffCreatePatientRequest.java:18>).

FE gọi Auth staffCreatePatient chỉ với fullName/phone/email. Auth tạo user Keycloak/auth_db, không tạo Patient profile; patientId không truyền nên null. FE lại hiển thị `patientId || id` như mã hồ sơ, mặc dù id ở đây là Auth userId. Thiếu email thì FE cho phép nhưng backend @NotBlank trả 400. Lookup hiện cũng tìm account Auth, không tìm Patient unlinked.

Đây không phải yêu cầu Auth ghi trực tiếp patient_db. Cần chốt orchestration: tạo/tra cứu hồ sơ qua Patient v3, có account thì liên kết bằng workflow trusted riêng, account là tùy chọn khi walk-in. Không dùng trùng phone/email để tự link; Patient.id và User.id phải tách biệt.

Nghiệm thu: tạo walk-in không account vẫn có Patient UUID và book được theo quyền STAFF; nếu tạo account thì mapping trỏ đúng hồ sơ; email optional của Patient không bị biến thành bắt buộc chỉ vì account DTO.

### A07 — Module Patient UI chưa nhận session và chưa có menu

Nguồn: [App truyền props](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/App.jsx:302>), [PatientModule contract](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/patient/PatientModule.jsx:41>), [UI permissions](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/patient/PatientModule.jsx:28>).

App chỉ truyền onBeforeLeave, không truyền session. PatientModule lấy token qua session.getAccessToken và permissions qua session.permissions, nên live mode chưa có identity bridge dù AuthContext đã login. Không menu nào đặt tab patient-records; nhánh render tồn tại nhưng người dùng không truy cập được qua menu hiện tại.

Việc FE/Patient UI sửa: nối session/token và permission display với host, thêm menu theo role/scope; Backend vẫn là nguồn quyết định quyền. Không tự cấp create/update permission chỉ để bật nút.

### A08 — Notification drawer gọi API admin cho mọi role; backend chưa bảo vệ

Nguồn: [drawer luôn được mount](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/App.jsx:336>), [drawer gọi admin list/retry](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/components/NotificationDrawer.jsx:32>), [NotificationController](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/api/controller/NotificationController.java:33>).

Mọi user có nút Notification, nhưng drawer gọi admin list và retry. Backend chưa authenticate/owner/admin guard. `/notifications/me` nhận userId Long từ query trong khi Auth userId là UUID. Adapter còn fallback mock khi request lỗi. Khi thêm quyền backend, UI hiện tại của PATIENT/DOCTOR sẽ lỗi hoặc tiếp tục che lỗi bằng mock.

Việc Notification/Auth/FE thống nhất: /me derive account UUID từ trusted principal, detail owner/admin, admin list/retry theo quyền được chốt; drawer người dùng gọi /me, admin có màn riêng; không dùng query userId tự khai làm quyền.

### A09 — Notification chưa đọc reminder-reference trước SMTP/retry

Nguồn: [delivery service](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/usecase/NotificationDeliveryService.java:38>), [projection provider](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/application/usecase/GetAppointmentReferenceService.java:34>).

Appointment đã kiểm reminder trước publish và cung cấp projection, nhưng Notification hiện claim rồi sendHtml, không có Appointment client. Lịch có thể bị hủy/đổi sau publish trong lúc queue/SMTP retry chậm, nên vẫn gửi nhắc giờ cũ. DB job cũng chưa giữ đầy đủ context để đối chiếu aggregateVersion/slot/due.

Việc Notification sửa: lưu đủ original reminder context, gọi GET `/api/v2/appointments/{id}/reminder-reference` bằng credential Notification riêng trước gửi/retry, đối chiếu version/status/schedule; suppress reminder cũ và không biến dependency outage thành “lịch vẫn hợp lệ”. Không gọi GET full appointment/không đưa clinical reason vào email.

### A10 — Auth còn các đường làm sai nguồn identity/mapping

Nguồn: [Google login](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/application/service/AuthApplicationService.java:124>), [register mapping](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/application/service/AuthApplicationService.java:78>), [OTP default](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/auth-service/src/main/resources/application.yml:99>).

Ba lỗi từ audit trước vẫn tái hiện ở component: Google email/googleId tự khai có thể dẫn tới yêu cầu token cho account được chọn; register nhận patientId client chọn rồi lưu như mapping; forgot-password default trả OTP cho caller. Siết self check ở Appointment không chữa được trường hợp Auth cấp một mapping giả thành self hợp lệ.

Việc Auth sửa trước nghiệm thu security: chỉ chấp nhận Google/OIDC được xác minh, bỏ password-reset fallback để login, đăng ký public để unlinked đến khi trusted link, không trả OTP ở môi trường thật. Cần negative tests, không chỉ token happy path.

### A11 — Session state/pending request FE chưa tách theo account ổn định

Nguồn: [actorSubject/fullName và patient lựa chọn](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/App.jsx:176>), [pending storage](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/appointment/ui.jsx:91>), [clearSession](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentFE/src/context/AuthContext.jsx:32>).

Pending dùng fullName làm namespace (trùng tên được), clearPending không được gọi khi logout. MainApp vẫn giữ walkinPatient qua nhánh LoginPage, nên login account khác có thể giữ patient được STAFF chọn trước và override self patientId. Backend hiện sẽ chặn sai owner, nhưng UI vẫn sai hồ sơ/pending và có thể không book được đến khi reload.

Việc FE sửa: dùng verified stable userId/subject cho namespace; reset selection/walkin/pending theo identity change; chủ động đối soát lệnh chưa rõ kết quả trong đúng account, không tự gửi pending của account khác.

### A12 — Credential gọi Doctor/recipient vẫn cần chốt riêng

Nguồn: [Doctor/slot calls](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/infrastructure/adapter/HttpAppointmentReferenceAdapter.java:73>), [ProviderHttp yêu cầu token](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/src/main/java/com/fclinic/appointmentservice/infrastructure/ProviderHttp.java:43>).

Token client_credentials mới chỉ dùng cho Patient. Doctor/slot/recipient vẫn dùng APPOINTMENT_SERVICE_TOKEN tĩnh, mặc định rỗng; provider client từ chối trước khi gửi HTTP nếu token thiếu. Không được giả định token chỉ có SVC_PATIENT_READ sẽ tự có quyền Doctor/recipient khi các provider được bảo vệ.

Việc Appointment phối hợp: thống nhất client-auth mechanism, audience/authority và refresh/provisioning cho từng provider; dùng credential phù hợp least privilege. Đây là gate cấu hình/contract; không tự khẳng định Doctor đã từ chối bearer khi hiện tại Doctor chưa có security.

## G01 — Gate môi trường, không sửa bằng cách bỏ kiểm tra quyền

Nguồn: [Patient group/config](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/patient-service/src/main/resources/application.yml:4>), [shared Compose Patient DB](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/docker-compose.yml:91>), [runbook security](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/docs/integration-readiness/09_SECURITY_INTEGRATION.md>).

- Patient identity/scope yêu cầu HTTPS; Auth mặc định HTTP. Cần server TLS/proxy + CA trust + URL đúng. Nếu bật TLS trên service đăng ký Eureka, kiểm tra thêm secure endpoint metadata/trust ở Gateway. Vite proxy Auth mặc định HTTP cũng phải đổi theo topology.
- Service account thực tế phải có appointment-service audience, SVC_PATIENT_READ, subject khớp registry Auth/Patient. Template mới không tự cập nhật realm/volume đang tồn tại.
- STAFF grant APPOINTMENT_* không đồng nghĩa được GET/search Patient. Wizard STAFF còn cần ADMIN_ASSIGNMENT_READ + ADMIN_PROFILE_READ, hoặc ADMIN_ASSIGNMENT_SEARCH + ADMIN_SEARCH theo luồng; intake cần CREATE; clinical permissions được cấp riêng. Tất cả thiếu grant thì deny là đúng.
- Shared Compose tạo patient_db; Patient mặc định trỏ patient_uuid và validate schema không auto-init. Cần fresh UUID DB/schema đã chuẩn bị. Auth seed patientId không tự sinh row tương ứng trong Patient: tạo hồ sơ hợp lệ và trusted-link đúng UUID, không dùng User.id hoặc UUID ngẫu nhiên thay thế.
- Bật discovery phù hợp khi dùng Gateway lb://. Chạy dev proxy đi thẳng service không chứng minh Gateway đã hoạt động.

Nghiệm thu môi trường: các provider thật, dữ liệu/realm mapping đồng nhất, cả Gateway và direct port đều qua negative authorization checks; không reset DB/volume để “làm sạch” lỗi tích hợp.

## G02 — CI chưa bao phủ các gate vừa có

Nguồn: [CI backend](<C:/Fall 2026/MSS301/clinic-appointment-system/.github/workflows/ci.yml:29>), [CI FE](<C:/Fall 2026/MSS301/clinic-appointment-system/.github/workflows/ci.yml:53>).

CI backend chỉ compile + test, không verify nên bỏ qua 80 Failsafe integration test của Patient; FE chỉ npm ci + build, không npm test. Cần thêm integration/contract/E2E gate phù hợp. Build xanh hiện tại không chứng minh các API/version/UUID/provider đã ghép được.

## Nội dung có thể gửi cho từng owner

**Doctor owner:** “Appointment và Auth đang dùng UUIDv4, nhưng Doctor main còn Long, thiếu published-slot endpoint và seed slot 30 phút. Nhờ bàn giao B03 UUID + `/internal/v1/doctors/{doctorId}/slots/{slotId}` có published, slot future đúng bác sĩ/60 phút; đồng thời chốt protected Doctor reference `/2` cho Patient và mapping doctorId cùng Auth. Appointment tiếp tục sở hữu occupancy.”

**Auth owner:** “Introspection/scope cho Appointment/Patient đã có implementation và contract checks, cần provision realm/subject/secret thật. Còn thiếu workflow trusted account↔Patient linking và recipient resolver; UI staff hiện tạo account patientId=null. Nhờ xử lý thêm ba lỗi Google login, public patientId mapping và OTP response trước nghiệm thu security. Không cần Auth ghi trực tiếp patient_db.”

**Notification owner:** “Producer phát schemaVersion3 với UUID; consumer/DB hiện còn Long/BIGINT nên chưa nhận được. Nhờ nâng UUID xuyên DTO/domain/DDL/API, bỏ userId giả, bảo vệ /me/admin, thêm client credential riêng đọc reminder-reference trước SMTP/retry để suppress reminder cũ. Chốt recipient contract với Auth/Appointment.”

**Frontend owner:** “Còn hai bộ API: wizard gọi Patient v2, doctor dashboard gọi Appointment v1 và fallback mock. Nhờ thống nhất Appointment v2/Patient v3, page/content, bearer/version/idempotency; nối PatientModule session/menu; sửa intake account≠profile, notification drawer theo role và reset state theo account. API lỗi phải hiện lỗi thật, không tự thành dữ liệu/thành công giả.”

**Patient owner:** “API v3 và Appointment medical-reference `/2` đã khớp ở contract checks. Cần phối hợp FE intake/session, provisioning hồ sơ UUID và trusted link Auth; cấp scope Patient riêng cho STAFF theo workflow. Doctor reference `/2` vẫn chờ Doctor provider thật; giữ fail closed, không nới quyền để chạy demo.”

**Ops/Integration lead:** “Nhờ chốt topology TLS/discovery, CA trust, UUID data/realm mapping và explicit assignments; template/env.example không tự cấu hình deployment. Compose riêng Appointment còn thiếu các env security mới. Cần smoke toàn chuỗi qua Gateway bằng provider thật, rồi mới xác nhận E2E.”

## Thứ tự khép luồng

1. Chặn các bypass Auth và Notification API không có quyền trước khi dùng dữ liệu thật.
2. Sửa Compose/env/TLS; chốt provisioning/UUID/assignments; giữ nguyên fail closed.
3. Doctor bàn giao UUID/published-slot; FE thống nhất version và onboarding; nghiệm thu booking/read/reschedule/cancel bằng provider thật.
4. Bổ sung recipient provider, Notification schema3 và reminder validation; kiểm email đúng recipient với queue delay/retry/cancel.
5. Đưa smoke/contract/integration checks vào CI. Mỗi owner gửi endpoint/DTO/credential profile và evidence thực tế, không chỉ ảnh UI/build pass.

Không đánh dấu queue khác tên là lỗi “không nhận được message” một cách máy móc: Appointment và Notification đang cùng bind exchange clinic.events với cùng bốn routing key, nên exchange vẫn có thể phát bản sao tới cả hai queue. Cần chốt queue topology/queue không có consumer để vận hành sạch; blocker rõ ràng hiện tại là recipient enrichment và payload/ID incompatibility.
