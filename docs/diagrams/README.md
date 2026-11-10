# Sơ đồ kiến trúc hiện tại — 10/11/2026

- `fclinic-current-architecture.png`: bản render 2560 × 1980 để chèn vào báo cáo.
- `fclinic-current-architecture.svg`: bản vector, có thể phóng to và sửa bằng công cụ đồ họa.
- `render-current-architecture.mjs`: mã nguồn dựng sơ đồ; chạy từ repository bằng `node docs/diagrams/render-current-architecture.mjs` để cập nhật SVG và bản HTML dùng cho render.

Sơ đồ mô tả cấu hình cục bộ đang chạy, gồm Auth HTTP :8085 và TLS scope proxy :8445. Khi triển khai Auth HTTPS trực tiếp, bỏ proxy cục bộ theo cấu hình triển khai.

Nguồn đối chiếu: Gateway routes, `PublishOutboxService`, `NotificationPayloadAdapter`, `AppointmentEventListener`, `ScopeAuthorityService`, `DoctorPatientAssignmentController`, và [quy tắc phân công](../integration/DOCTOR_PATIENT_ASSIGNMENTS.md). Quyền đọc hồ sơ lấy từ lịch CONFIRMED/COMPLETED đã commit. Lỗi UUID → Long trong sơ đồ cũ không còn đúng với consumer schema v3 hiện hành.

Snapshot kiểm tra lúc render: cả 7 endpoint health trả 200; notification DB có 14 hàng SENT; queue `notification.appointment.q` có 1 consumer và 0 message chờ. Các con số này là snapshot, không phải điều kiện cố định của kiến trúc.
