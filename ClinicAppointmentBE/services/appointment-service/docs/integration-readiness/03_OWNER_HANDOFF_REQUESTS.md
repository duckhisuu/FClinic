# 03 — Việc từng nhóm cần bàn giao

Đây là checklist trong repository để trao đổi khi push, chưa gửi tin nhắn hay tự thay đổi source của nhóm khác.

## Doctor owner

Cung cấp ba API profile/offers/published-slot của02 và response đúng schema. Published slot phải đúng doctor, whole-hour 60 phút, không đổi lịch đã công bố; quản lý overlap/publication trong DB của Doctor. Seed30 phút không thuộc baseline. Không gọi ngược Appointment để quyết định occupancy, không yêu cầu Appointment update isBooked. Cho test vectors: active/inactive/missing doctor, published/unpublished/missing/wrong-doctor slot, malformed response, timeout. Appointment không có fallback từ published-slot sang list cũ.

## Patient owner

Cung cấp GET patient UUID/full14fields và dữ liệu ACTIVE/INACTIVE/404 đúng wrapper; phone E.164 và patientCode PAT-{uuid}. Phone dùng chung không phải identity. Xác nhận credential/read scope phía security hiện có cho Appointment. Khi clinical create cần Appointment facts, dùng projection4fields ở02 hoặc thống nhất route/version của profile đã có; không lấy tên/phone/reasonForVisit làm reference facts. Không tự đổi lịch sang COMPLETED và không coi NO_SHOW là đã khám.

## Auth / security owner

Giữ code security trong phạm vi của nhóm phụ trách. Bàn giao cách đưa Principal đã xác minh vào Appointment và thực thi self/scoped STAFF/assigned Doctor/SERVICE reads theo Report. Human authorization phải trước machine lookup và phải được kiểm tra lại khi replay. Hiện module chỉ nhận Principal để namespace/audit, không cài guard mới; do đó chưa có security acceptance. Không dùng browser X-User-Id/X-Role/X-Patient-Id làm nguồn tin cậy.

## Recipient provider và Notification owner

Chốt D-08 route/credential/request/response và nguồn mapping tài khoản. Adapter Appointment đề xuất POST `{patientId,doctorId}` tới APPOINTMENT_RECIPIENT_URL; response đủ PATIENT+DOCTOR, mỗi recipient có type/userId/name/email. Patient userId và email có thể null; Doctor userId phải số dương. Không dò tài khoản qua trùng điện thoại/email. Khi nguồn unavailable Appointment giữ pending enrichment, booking vẫn đã commit.

Consume bốn event schema v2 ở04, dedup eventId và lưu context cần cho retry/restart. Reminder phải đọc projection hiện tại, đối chiếu CONFIRMED/version/schedule/due trước delivery; event cũ sau cancel/reschedule cần bị bỏ. Cần thống nhất minimum read route/scopes với nhóm security. Xác nhận lead/cadence D-09 và thực hiện template/retry/DLQ/SMTP phía Notification; Appointment không chứng minh email đã gửi khi broker ACK.

## Gateway / Frontend / Operations owner

Gateway nối /api/v2/appointments và Patientv2; chuyển tiếp Idempotency-Key, Authorization theo security hiện có, X-Request-Id; expose correlation header qua CORS. Gateway không được sửa trong task này. React Appointment dùng API v2, không gửi snapshots, retry giữ nguyên key/body, xử lý409 có tải lại. Các màn khác/adapter cũ giữ nguyên.

Ops tạo DB riêng appointment_db (hoặc APPOINTMENT_DB_NAME), secret env riêng, chạy Flyway trên DB trống. Nếu có dữ liệu v1: inventory/backup/stop writers/map UUID/rehearsal/restore trước cutover; không drop/backfill tự động khi ghép môi trường chung. Cung cấp RabbitMQ và các provider URLs/credentials. Không commit env/token hoặc đưa service token vào VITE_*.

## Thông tin cần ghi khi ghép

Mỗi boundary ghi owner, version/hash contract, base URL môi trường, cơ chế credential hiện có, test vectors/kết quả và commit/deployment thực tế. Artifact hash đúng chỉ chứng minh cùng file schema, không chứng minh service đã triển khai hoặc real E2E đã pass.
