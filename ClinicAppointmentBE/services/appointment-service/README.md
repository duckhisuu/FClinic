# Appointment v2

Phạm vi: nghiệp vụ Appointment BE và giao diện React trong `ClinicAppointmentFE/src/appointment`. Report được giữ nguyên và dùng làm chuẩn cho nghiệp vụ. Không bổ sung security, không sửa service khác. Hướng dẫn bàn giao đầy đủ: [docs/README.md](docs/README.md).

## Cấu trúc theo bản pull

Giữ đủ 37 file Java gốc: controller/dto/command/port/in/port/out/usecase/domain/JPA adapter/entity/repository/config. AppointmentApplicationService triển khai booking; hủy, đổi lịch, truy vấn, availability, history, references, reminder và outbox có use case riêng. Application chỉ phụ thuộc port/domain, không gọi trực tiếp JDBC/HTTP/RabbitMQ. Xem [mapping các use case](docs/integration-readiness/08_USE_CASE_ARCHITECTURE.md).

## Nghiệp vụ đã triển khai

- Tạo lịch `CONFIRMED` bằng `patientId` UUID, `doctorId`, `slotId`, `reasonForVisit` tùy chọn. Tên, điện thoại, chuyên khoa, phí và giờ khám lấy từ dịch vụ nguồn; không nhận snapshot do trình duyệt gửi.
- Bệnh nhân phải ACTIVE, bác sĩ active; slot do Doctor trả về, đúng bác sĩ, trong tương lai, bắt đầu đúng giờ và dài 60 phút theo Asia/Saigon. Xác minh slot bằng contract Report `GET /internal/v1/doctors/{doctorId}/slots/{slotId}` có published, và đọc danh sách offer qua `GET /api/v1/doctors/{id}/slots?date=...`; không thêm endpoint bên Doctor, không dùng `isBooked` làm nguồn occupancy.
- PostgreSQL partial unique `(doctor_id, appointment_date, start_time) WHERE status='CONFIRMED'` chặn tranh chấp kể cả hai slotId khác nhau cùng giờ. Redis không quyết định việc giữ chỗ.
- Hủy/đổi lịch chỉ khi CONFIRMED, trước giờ bắt đầu, đúng version. Đổi lịch giữ nguyên bệnh nhân, bác sĩ và snapshot phí; lỗi khi giữ giờ mới rollback toàn bộ, giữ giờ cũ.
- Appointment, history, operation result và outbox intent commit trong cùng unit of work qua TransactionTemplate/JpaTransactionManager; JPA và các adapter JDBC cục bộ chia sẻ datasource/transaction. HTTP tới dịch vụ khác nằm ngoài transaction ghi.
- Mỗi POST/PUT bắt buộc Idempotency-Key UUID. Hash gồm HTTP method, resource path và body đã chuẩn hóa. Lưu nguyên HTTP status/body để gửi lại chính xác; replay trước kiểm tra version cũ. Khóa đang xử lý trả OPERATION_IN_PROGRESS, đổi nội dung cùng khóa trả IDEMPOTENCY_CONFLICT.
- COMPLETED/NO_SHOW là trạng thái kết thúc. Không tự tạo API hoàn thành/vắng khám vì Report chưa định nghĩa thao tác này.

## API

Tất cả dùng wrapper `{status,message,data}`; lỗi thêm `code,fieldErrors`. Không có mock fallback.

| Method | Đường dẫn |
| --- | --- |
| POST | /api/v2/appointments |
| GET | /api/v2/appointments?patientId=&doctorId=&date=&status=&page=0&size=20 |
| GET | /api/v2/appointments/{id} |
| PUT | /api/v2/appointments/{id}/cancel |
| PUT | /api/v2/appointments/{id}/reschedule |
| GET | /api/v2/appointments/available-slots?doctorId=&date=YYYY-MM-DD |
| GET | /api/v2/appointments/{id}/history |
| GET | /api/v2/appointments/{id}/medical-reference |
| GET | /api/v2/appointments/{id}/reminder-reference |

Create chỉ nhận `{patientId,doctorId,slotId,reasonForVisit?}`. Cancel nhận `{version,reason?}`. Reschedule nhận `{version,slotId,reason?}`. Lý do thay đổi tối đa 500 ký tự. JSON sai kiểu số, field thừa và tham số không hỗ trợ bị từ chối.

List trả `{content,page,size,totalElements,totalPages}`, tối đa 100/trang, sắp xếp ngày/giờ/id giảm dần. History có action/version, giờ cũ/mới, lý do và thời điểm. Medical-reference chỉ trả `id,patientId,doctorId,status`; reminder-reference thêm version, slot, lịch và timezone, không có lý do khám. Đây là các read model bổ sung trong phạm vi Appointment để khép phần DTO còn thiếu của Report.

## Ranh giới security giữ nguyên

Theo yêu cầu không code thêm security: không có login, Auth introspection, JWT filter, role guard, cấp scope nhân viên hay endpoint session mới. Security và giới hạn truy cập do phần tích hợp hiện có chịu trách nhiệm. Các query filter chỉ lọc dữ liệu nghiệp vụ, không phải phân quyền.

Khi container có Principal, tên principal dùng làm namespace idempotency/audit. Khi chưa có, namespace kỹ thuật là `anonymous`; history ghi actorRole SYSTEM, không tự nhận đã xác minh PATIENT/STAFF. Vì vậy kết quả kiểm thử ở đây chỉ chứng minh nghiệp vụ, không chứng minh phần phân quyền theo Report. Không nhận header tự khai danh tính làm principal.

## Chạy cục bộ

Java 21, Maven, PostgreSQL và RabbitMQ. Từ `ClinicAppointmentBE`:

```sh
mvn -pl services/appointment-service -am clean package -Dmaven.test.skip=true
cd services/appointment-service
java -jar target/appointment-service-1.0.0-SNAPSHOT.jar
```

Module đọc file `.env` riêng đã được Git bỏ qua. DB dùng DB_USERNAME/DB_PASSWORD, POSTGRES_APPOINTMENT_PORT (mặc định 5435), APPOINTMENT_DB_NAME (mặc định appointment_db). Flyway tạo schema v2 trên DB trống. Không baseline/migrate âm thầm dữ liệu v1 chứa patient_id bigint hoặc PENDING; không chạy writer v1 cùng schema v2.

Cấu hình Appointment trong env:

| Biến | Ý nghĩa |
| --- | --- |
| APPOINTMENT_PATIENT_URL | Patient hiện có, mặc định http://localhost:8081 |
| APPOINTMENT_DOCTOR_URL | Doctor hiện có, mặc định http://localhost:8082 |
| APPOINTMENT_SERVICE_TOKEN | Credential do cơ chế security hiện có cấp để đọc provider; không tự tạo tài khoản/token |
| APPOINTMENT_RECIPIENT_URL | URL tra người nhận do dịch vụ nguồn cung cấp; để trống thì outbox giữ pending |
| APPOINTMENT_LOCK_PROVIDER | postgres mặc định; redis bật Redisson adapter có sẵn; capacity vẫn do unique PostgreSQL |
| APPOINTMENT_OUTBOX_ENABLED | true/false, mặc định true |
| APPOINTMENT_REMINDER_LEAD | ISO-8601 Duration dương, để trống thì chưa lên lịch nhắc; không tự chọn chính sách |
| APPOINTMENT_OUTBOX_POLL_MS | Mặc định 5000 |
| APPOINTMENT_REMINDER_POLL_MS | Mặc định 30000 |

Provider dùng wrapper chuẩn. Recipient adapter POST `{patientId,doctorId}`, nhận data là đúng hai phần tử `{recipientType,userId,name,email}` PATIENT và DOCTOR; userId DOCTOR bắt buộc, email được null. Không dò tài khoản qua điện thoại/email, không tạo người nhận giả.

## Outbox và nhắc lịch

Publish JSON thô schemaVersion 2 tới topic `clinic.events`, queue `fclinic.appointment.notifications`, routing `appointment.created/cancelled/rescheduled/reminder`. EventId ổn định từ lúc tạo intent; payload cuối đóng băng nguyên byte khi resolve người nhận. Không có reasonForVisit, lý do hủy/đổi, chẩn đoán hay ghi chú trong event.

Dùng lease claim và SKIP LOCKED; retry sau lỗi provider/broker, không mất intent khi khởi động lại. Chỉ ghi PUBLISHED sau publisher ACK và không có mandatory return. Vẫn là at-least-once: consumer cần chống lặp theo eventId. Nhắc lịch có unique appointment/version/due, kiểm tra trạng thái/version/giờ hiện tại trước publish và suppress reminder cũ.

Gateway hiện có vẫn giữ route cũ vì không được sửa file ngoài Appointment. Phía sở hữu Gateway cần nối v2 nếu chạy qua cổng 8080; dev React dùng proxy trực tiếp. Doctor owner triển khai published-slot contract và slot 60 phút theo Report; Appointment không hạ contract để tương thích code/seed 30 phút hiện tại. Notification hiện tại cần consumer tương thích schema v2/queue này; module không sửa consumer hoặc khẳng định đã gửi email.

## Kiểm chứng

Bộ kiểm tra ở `src/test/python/test_appointment_v2.py`, luôn được gitignore theo yêu cầu. Nó tạo DB/vhost ngẫu nhiên, dùng PostgreSQL/RabbitMQ thật, provider HTTP là test doubles, đối chiếu JSON schema nguyên bản trong Report; dọn DB/vhost sau khi kết thúc. Không chạy trên appointment_db cấu hình thật và không kiểm thử security.

Đã kiểm tra tranh chấp cùng giờ, strict DTO, snapshot, replay, version, rollback khi history/result/outbox lỗi, bốn event schema, pending enrichment, mandatory return, khôi phục lease/payload sau restart và reminder cũ. Giao diện được kiểm tra bằng trình duyệt cho chọn giờ, đặt, đọc lại, đổi/hủy và lịch sử.
