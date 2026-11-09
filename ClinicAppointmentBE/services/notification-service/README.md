# Notification Service — phân quyền người nhận

## Quyền truy cập

- `GET /api/v1/notifications/me`: lấy UUID tài khoản từ claim `fclinic_user_id` trong JWT đã xác minh. Không nhận `userId` để lựa chọn hộp thư.
- `GET /api/v1/notifications/{id}`: chỉ chủ thông báo đọc được; ID không thuộc tài khoản trả `404`.
- `GET /api/v1/admin/notifications`, `GET /api/v1/admin/notifications/{id}` và `POST /api/v1/admin/notifications/{id}/retry`: chỉ `ROLE_ADMIN`. Staff không được xem toàn bộ thông báo.
- JWT phải có chữ ký hợp lệ, đúng issuer, còn hạn, audience `notification-service`, UUIDv4 tài khoản và vai trò người dùng hợp lệ. Không dùng `sub`, `patient_id`, `doctor_id` hay email thay cho UUID tài khoản.
- Chuông thông báo luôn dùng hộp thư cá nhân, kể cả khi người đăng nhập là admin. Lỗi API không chuyển sang dữ liệu mock.

## Cấu hình Keycloak

`infra/keycloak/fclinic-realm.json` bổ sung audience mapper `fclinic-audience-notification-service` trong client scope `fclinic-claims`. Với realm đã tồn tại, cập nhật mapper qua Keycloak Admin API/console; `--import-realm` không cập nhật realm đã có. Người dùng cần đăng nhập lại để nhận audience mới.

`KEYCLOAK_ISSUER_URI` cấu hình issuer; `NOTIFICATION_JWK_SET_URI` có thể ghi đè địa chỉ lấy khóa công khai. Nếu không ghi đè, khóa được lấy từ `<issuer>/protocol/openid-connect/certs`.

## Event và dữ liệu

- ID notification, template, event, appointment và tài khoản người nhận đều dùng UUID. Endpoint detail/retry chỉ nhận UUID, không nhận ID số.
- Consumer nhận event `source=appointment-service`, `schemaVersion=3`, UUID appointment và UUID tài khoản người nhận.
- Mỗi event phải có đúng một người nhận `PATIENT` và một `DOCTOR`; không suy đoán tài khoản từ tên, email hoặc doctor ID.
- Patient tại quầy chưa có tài khoản có thể nhận email với `userId=null`; thông báo đó không xuất hiện trong hộp thư của bất kỳ tài khoản nào. Doctor phải có UUID tài khoản.
- Email giống nhau không làm gộp thông báo của hai người nhận. Event lặp không tạo thêm thông báo.
- Flyway V4 sao lưu toàn bộ thông báo BIGINT cũ vào `notifications_legacy_id_archive`, rồi chuyển bảng hoạt động sang UUID. Bản cũ chỉ để đối soát, không tự gửi lại hoặc tự gán vào tài khoản UUID. Nếu cần đưa dữ liệu cũ trở lại hộp thư, phải có ánh xạ tài khoản đã xác minh.
- Flyway V5 cấp UUIDv4 mới cho khóa chính notification đang hoạt động và template. Các hàng notification hiện có được giữ nguyên nội dung/trạng thái/người nhận; `notification_id_migration_map` lưu ID notification cũ và UUID mới để đối soát. API không cung cấp fallback từ ID số.

## Kiểm tra

```bash
mvn -pl services/notification-service -am test -B
```

Các bài kiểm tra bao gồm: inbox hai tài khoản khác nhau (kể cả cùng email), giả mạo query `userId`, đọc chéo notification ID, quyền admin/retry, JWT sai chữ ký/issuer/audience/hết hạn, thiếu UUID người nhận, UUID event v3 và chống event lặp.

Đã kiểm tra cục bộ bằng event v3 tổng hợp qua RabbitMQ → PostgreSQL → SMTP/MailHog: hai thông báo `SENT`, mỗi email chỉ có một người nhận; inbox patient/doctor có đúng thông báo của mình, staff/admin không thấy trong inbox cá nhân. Đây là kiểm tra pipeline Notification; chưa chứng minh luồng tạo booking thật vì recipient resolver phía Appointment vẫn là hạng mục tích hợp riêng.
