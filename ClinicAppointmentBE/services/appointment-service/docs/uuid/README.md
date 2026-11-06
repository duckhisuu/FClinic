# UUIDv4 và Patient v3 — chỉ trong Appointment Service

Phạm vi hiện tại: sửa Appointment sau merge main ngày 02/11/2026, giữ cấu trúc use case và đủ các file gốc. Appointment gọi profile Patient v3 và thống nhất business ID là UUIDv4. Không sửa Patient/Doctor/Notification, common, Gateway, FE hoặc cấu hình hạ tầng chung; không thêm security. Có Dockerfile/Compose/env mẫu riêng của Appointment, xem [hướng dẫn Docker](DOCKER.md).

## Các định danh của Appointment

ID appointment, history, operation result, outbox event và reminder publication là UUIDv4. Các khóa tham chiếu patientId, doctorId, slotId và recipient userId bên trong Appointment cũng là UUIDv4. API body/path/query và dữ liệu provider phải đúng phiên bản này; UUIDv1/v3/v5, nil và ID số bị từ chối. Version, tổng bản ghi, pagination, retry count và thời gian chờ giữ kiểu số. Idempotency-Key/X-Request-Id là khóa kỹ thuật riêng, giữ hợp đồng UUID hiện có.

DTO/command/port/domain/JPA/JDBC và API Appointment đã đồng bộ kiểu này. Các so sánh UUID dùng giá trị (`equals`) để không nhầm hai object cùng ID. Quy tắc CONFIRMED, hủy/đổi trước giờ khám, cùng bác sĩ, slot 60 phút, optimistic version, idempotency và unique doctor/hour giữ nguyên.

## Hợp đồng bàn giao cho thành viên khác

- Create: `{patientId, doctorId, slotId, reasonForVisit?}` với các ID là chuỗi UUIDv4.
- Cancel: `{version, reason?}`; reschedule: `{version, slotId, reason?}` với slotId UUID.
- Detail/history/medical-reference/reminder-reference trả ID UUID.
- Appointment đọc `GET /api/v3/patients/{patientId}`; không fallback v2/v1. Status ACTIVE/INACTIVE, envelope và snapshot profile giữ nguyên. Patient v3 cần SERVICE credential/quyền hiện có; lỗi Auth/Scope503 vẫn là DEPENDENCY_UNAVAILABLE, không giả PATIENT_NOT_FOUND.
- Appointment cần Patient/Doctor/provider trả UUIDv4 theo hợp đồng; không chuyển ID số/UUIDv3 của implementation hiện tại thành UUIDv4 một cách ngầm định khi gọi HTTP.
- Lifecycle event mới dùng `schemaVersion: 3` vì các ID đã đổi kiểu. Consumer cần cập nhật đồng bộ trước khi nhận event UUID; queue/routing/config hiện có được giữ nguyên.

[OpenAPI Appointment UUID](contracts/appointment.openapi.json) và bốn event schema [created](contracts/created.event.schema.json), [cancelled](contracts/cancelled.event.schema.json), [rescheduled](contracts/rescheduled.event.schema.json), [reminder](contracts/reminder.event.schema.json) nằm trong thư mục này. Đây là contract của Appointment để nhóm khác nối tiếp, không phải xác nhận các service khác đã triển khai UUID.

## Patient đọc medical-reference

Nhóm đã thống nhất dùng HTTP nội bộ. Collection URI của client Patient trỏ tới `http://<appointment-host>:8083/api/v2/appointments` (host/port theo môi trường), rồi gọi `GET /{appointmentId}/medical-reference` bằng UUIDv4. Không thay đổi security. Client Patient hiện đã dùng version `/2`; URI local mặc định của Patient còn `/internal/v1/appointments`, owner cần đặt lại collection URI và cho phép hostname Docker khi chạy giữa các container.

Appointment trả đúng một header `X-FClinic-Reference-Contract: patient-appointment-medical-reference/2` trên response của GET endpoint này, bao gồm 200, 404 và các lỗi do Appointment xử lý. Header được đặt trước validation để lỗi UUID hoặc X-Request-Id vẫn có phiên bản contract. Header chỉ mô tả contract, không xác thực hoặc cấp quyền. Không gắn header này cho API detail, reminder, các method khác hay v1 đã ngừng dùng.

Response 200 giữ envelope `{status, message, data}`; `data` chỉ gồm `{id, patientId, doctorId, status}` với ba ID là chuỗi UUID. Response 404 giữ `code: APPOINTMENT_NOT_FOUND`. Các trạng thái vẫn là `CONFIRMED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`.

**Hai bên dùng `/2` với UUIDv4; không đổi version chỉ vì profile Patient chuyển sang v3.** `/1` là contract ID số cũ, không được gắn nhãn lên payload UUID. Appointment luôn công bố `/2`, không echo phiên bản do client gửi và không thương lượng hoặc chuyển về ID số. Đường dẫn không tồn tại trả404, method không được hỗ trợ trả405 kèm Allow; không tạo endpoint nội bộ giả để che URI client sai. Việc cấp credential theo security hiện có do nhóm phụ trách.

## Migration cục bộ

V1/V2 đã phát hành được giữ nguyên checksum. V2 cũ chuyển số thành UUIDv3; **không dùng UUIDv3 đó làm contract hiện hành**. V3 tạo bảng bền vững `appointment_uuid_v4_mapping(resource_type, old_id, new_id)`; V4 chuyển dữ liệu trong database Appointment sang UUIDv4 và bổ sung CHECK constraints. Không kết nối hoặc sửa database Patient/Doctor/Notification.

DB mới rỗng chạy V1–V4 tự động. Với DB cũ, dừng writer/job/consumer và triển khai V3 trước cutover. Owner cung cấp mapping Patient/Doctor/slot/user thực tế nếu còn ID ngoài v4, rồi mới chạy V4. Ví dụ schema nạp mapping (thay placeholder bằng ID được owner bàn giao):

```sql
INSERT INTO appointment_uuid_v4_mapping(resource_type,old_id,new_id)
VALUES ('doctor','<old-uuid>'::uuid,'<doctor-uuidv4-from-owner>'::uuid);
```

V4 tự tạo **UUIDv4 ngẫu nhiên thật** và lưu mapping cho ID Appointment sở hữu (appointment/history/operation/event/reminder); giữ nguyên ID v4 đã có. V4 không tự phát minh ID Patient/Doctor/slot/user. Thiếu mapping của owner thì migration fail và rollback toàn bộ V4, để V3 còn nguyên cho việc chuẩn bị. Có thể export mapping ID Appointment để các consumer đối soát:

```sql
SELECT resource_type,old_id,new_id FROM appointment_uuid_v4_mapping ORDER BY resource_type,old_id;
```

V4 giữ liên kết FK, version, patient v4 hiện có; cập nhật cached response và intent chưa công bố khi ID thay đổi. Nếu response đã hoàn toàn v4 thì giữ nguyên byte cache. Snapshot PUBLISHED/SUPPRESSED giữ nguyên làm lịch sử và không phát lại như event mới. Cần drain payload READY trước cutover; migration chặn nếu còn READY. Không tái dùng idempotency key của body/path ID cũ cho body/path mới. Client/consumer phải đối soát mapping và đổi link/reference cũ trước khi mở ghi; không chạy binary cũ trên schema mới.

Không đổi application.yml, env thật, POM, config class, HTTP credential handling hay shared compose. Dockerfile/Compose và `.env.example` được thêm riêng trong Appointment để phân biệt hostname/cổng nội bộ với cổng host. Task này không triển khai security hoặc thay cơ chế Eureka.

## Kiểm chứng đúng phạm vi

Build Appointment và các dependency common sẵn có bằng Maven. Kiểm thử Docker build image từ Dockerfile hiện hành, chạy bằng Compose riêng và PostgreSQL/RabbitMQ tạm. Patient JAR thật được dùng để xác nhận DNS HTTP/v3 và fail-closed503 khi thiếu Auth/Scope; positive booking/reschedule/cancel dùng Patient v3/Doctor UUIDv4 HTTP contract fixtures. Không gọi đó là positive clinical flow qua mọi service thật. Không sửa service khác hoặc migrate/reset Docker volume đang dùng.

Kết quả hiện hành nằm trong [verification.json](verification.json). Test/log/env cục bộ được gitignore. Những phụ thuộc còn chờ owner: Auth/Scope và SERVICE provisioning, Doctor UUIDv4/internal slot, Notification event schema3, Gateway route mới, Patient HTTP DNS và collection URI. Appointment không giả dữ liệu hay sửa service khác để che các phụ thuộc này.
