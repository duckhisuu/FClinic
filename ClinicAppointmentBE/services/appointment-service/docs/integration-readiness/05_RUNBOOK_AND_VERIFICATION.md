# 05 — Chạy, cấu hình và kiểm chứng

## Chạy module độc lập

Java21 + Maven; PostgreSQL và RabbitMQ của môi trường riêng. Từ ClinicAppointmentBE: `mvn -pl services/appointment-service -am clean package -Dmaven.test.skip=true`, sau đó chạy JAR từ services/appointment-service. `.env` riêng được Git bỏ qua, DB credentials hiện tại sa/sa trong máy phát triển; không đưa credential thật vào doc/contract.

Các biến: DB_HOST/DB_USERNAME/DB_PASSWORD, POSTGRES_APPOINTMENT_PORT(default5435), APPOINTMENT_DB_NAME(defaultappointment_db), RABBITMQ_HOST/PORT/DEFAULT_USER/DEFAULT_PASS. Thêm APPOINTMENT_PATIENT_URL, APPOINTMENT_DOCTOR_URL, APPOINTMENT_SERVICE_TOKEN đã cấp, APPOINTMENT_RECIPIENT_URL. Outbox mặc định true, poll5000ms; APPOINTMENT_REMINDER_LEAD cần Duration dương do nghiệp vụ chọn, blank thì chưa tạo reminder; reminder poll mặc định30000ms.

APPOINTMENT_LOCK_PROVIDER=postgres dùng idempotency guard PostgreSQL độc lập transaction JPA; redis bật RedissonDistributedLockAdapter/RedisLockConfig gốc qua REDIS_HOST/PORT/PASSWORD. Unique PostgreSQL doctor/hour vẫn luôn bắt buộc; không phải chọn Redis là bỏ DB constraint.

Không trỏ URL sang API khác hoặc gỡ validation để làm provider cũ chạy được. Chưa có provider đúng contract thì giữ lỗi503/pending tương ứng và kiểm thử module bằng substitute được chỉ rõ. Không thêm endpoint bên provider, không đọc DB service khác.

Flyway fresh-v2 V1 tạo5bảng + flyway_schema_history; reject schema cũ không tương thích. Không baseline-on-migrate. Runbook chuyển dữ liệu v1 cần plan riêng, không chạy DROP trên shared DB theo hướng dẫn kiểm thử này.

React: từ ClinicAppointmentFE chạy npm ci, npm run dev, npm run build. Vite dev mặc định proxy Appointment:8083/Patient:8081/Doctor:8082; dùng VITE_*_PROXY_TARGET cho môi trường riêng. Prod cần Gateway owner nối v2. Không đưa service token vào React.

## Kết quả đã có và giới hạn

Bộ kiểm tra cục bộ nằm trong src/test/python và test/runtime, được gitignore theo yêu cầu nên không được coi là test runner đã push. Nó tạo DB/vhost ngẫu nhiên, chạy JAR thật, provider HTTP là test doubles tổng hợp; PostgreSQL/RabbitMQ thật. Không dùng appointment_db chung. Kết quả cuối nằm ở target/v2-check/results.json cục bộ; tài liệu06 phải được đọc cùng giới hạn evidence.

| Nhóm ca kiểm tra | Expected |
| --- | --- |
| Minimal request/DTO | UUID Patient, strict numeric ID, reject snapshots/extra fields; response đúng schema Report |
| Provider failures | Missing/inactive/malformed/timeout không tạo booking; không default id/fee/active |
| Race capacity | 12 request cùng doctor/date/hour qua2slotIds → đúng1commit; còn lại409 |
| Atomicity | Buộc INSERT history/operation_result/outbox lỗi trong DB test → appointment không bị tạo mồ côi |
| Replay | Gửi lại cùng key/body → nguyên status/body, một history/result/outbox; đổi body409 |
| Change | Đổi sang slot đã chiếm rollback và giữ giờ cũ; hủy/đổi cùng version chỉ1winner; terminal/past reject |
| Broker | Cả4event qua RabbitMQ thật và validate schema Report; không clinical fields |
| Recovery | Recipient outage giữ intent; mandatory unroutable không PUBLISHED; phục hồi giữ payload/eventId; restart reclaim lease |
| Reminder | Unique version/due qua restart; cancel trước delivery suppress reminder cũ |
| React | Chọn bác sĩ/ngày/slot, chọn hồ sơ tồn tại, đặt/đọc lại/đổi/hủy/history, chọn slot không tự submit |

## Khi chạy integration thật sau khi các nhóm hoàn thành

1. Pin commit/deployment, env đã redaction và contract hash từng boundary.
2. Chạy direct module và qua Gateway cùng credential do security hiện có cấp. Kiểm tra deny/outage và không ghi DB khi reference unusable.
3. Đặt một lịch thật trong test environment, đọc lại snapshot/version, gửi lại cùng key. Cho hai client tranh cùng giờ và đo DB thật, không chỉ HTTP response.
4. Theo dõi outbox→RabbitMQ→Notification→SMTP test mailbox, dedup và reminder stale sau cancel/reschedule/restart.
5. Ghi expected/actual/requestId/eventId/DB assertions; giữ token/body khám khỏi log/doc. Đừng đánh dấu toàn hệ thống đã xong chỉ từ component tests này.

Đây là procedure bàn giao, không tự chạy tests thay các nhóm trên production hoặc sửa service khác.
