# Appointment — tài liệu bàn giao

Cập nhật 27/10/2026. Đọc thư mục này trước khi tích hợp Appointment. Nghiệp vụ lấy Report làm chuẩn; không sửa Patient, Doctor, Notification, Gateway hoặc security để chạy theo implementation hiện tại của từng nhóm.

Giữ 37 file Java gốc, xử lý qua input/output ports và các use case riêng: [sơ đồ và mapping lớp](integration-readiness/08_USE_CASE_ARCHITECTURE.md).

## Đọc theo thứ tự

1. [Phạm vi và nghiệp vụ](integration-readiness/01_APPOINTMENT_IMPLEMENTATION.md).
2. [Contract Appointment cung cấp và cần từ service khác](integration-readiness/02_PROVIDER_CONSUMER_CONTRACTS.md).
3. [Việc từng nhóm cần bàn giao](integration-readiness/03_OWNER_HANDOFF_REQUESTS.md).
4. [API, payload và sự kiện](integration-readiness/04_API_AND_EVENTS.md).
5. [Cấu hình, chạy và kiểm chứng](integration-readiness/05_RUNBOOK_AND_VERIFICATION.md).
6. [Các điểm cần chốt khi ghép hệ thống](integration-readiness/06_INTEGRATION_GAPS.md).
7. [Kết quả kiểm chứng và giới hạn](integration-readiness/07_VERIFICATION_SUMMARY.md).

Máy có thể đọc [OpenAPI nghiệp vụ hiện tại](integration-readiness/contracts/appointment-business.openapi.json), [bản contract gốc của Report](integration-readiness/contracts/report/appointment.openapi.json), [event schemas](integration-readiness/contracts/events/created.event.schema.json) và [JSON mẫu](integration-readiness/fixtures/booking-request.json). Các bản gốc được copy nguyên byte, có [SHA-256](integration-readiness/contracts/report-artifact-pins.json). Report không bị sửa và không cần có trong Git để đồng đội đọc các contract này.

**Đã triển khai:** nghiệp vụ Appointment BE, React, migration DB mới, idempotency/history/outbox/reminder. **Chưa xác nhận:** hệ thống ghép với provider thật, Gateway và gửi email thật. HTTP provider trong kiểm thử là dữ liệu tổng hợp; PostgreSQL/RabbitMQ là thật. Security mới nằm ngoài phạm vi theo yêu cầu: không có login/JWT/role/scope mới, các kiểm tra truy cập do phần security hiện có/nhóm phụ trách thực hiện.

Không lấy các mô tả lịch sử của Patient docs hoặc trạng thái PROPOSED/NOT IMPLEMENTED trong bản Report gốc làm bằng chứng rằng code hiện tại đã tích hợp thành công. Tài liệu này ghi riêng phần đã chạy và phần cần các nhóm nối tiếp.
