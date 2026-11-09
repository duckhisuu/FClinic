# Kết quả sửa tích hợp Appointment / Auth / Patient / Gateway

Phạm vi được yêu cầu: xác thực và authorization Appointment; nối SERVICE registry/introspection/scope với Patient; bảo vệ Patient → Appointment medical-reference; route Gateway Appointment v2. Chưa triển khai lên môi trường đang chạy và không đổi `.env` thật, database/realm/volume hiện có.

## Thay đổi

- Appointment xác minh original bearer bằng Auth introspection, loại anonymous fallback và header tự khai danh tính. Áp self PATIENT, assigned DOCTOR và explicit STAFF assignment tại các use case. Authorization chạy trước idempotency replay; query scope áp trước phân trang/count và audit ghi role đã xác minh.
- Auth hỗ trợ credential introspection riêng cho Appointment, xác minh mapping bằng exact Keycloak subject/current activation và không tự điền audience thiếu. Thêm scope provider được bảo vệ bằng credential riêng theo contract/authority; assignment lấy qua application port từ protected deployment configuration. Thiếu/expired assignment deny.
- Thống nhất SVC_PATIENT_READ trong Keycloak template, Auth policy và Patient registry. Cung cấp profile group Patient service-integration và registry subject explicit. Machine token Appointment dùng client_credentials có cache/refresh trước hạn hoặc credential SERVICE được provision rõ ràng.
- Patient local reference URI chuyển sang `/api/v2/appointments`. Appointment xác thực Basic credential riêng chỉ trên medical-reference; credential đó không đọc full detail/history hoặc dùng mutation. Giữ projection UUIDv4 và header contract `/2`.
- Gateway route v2 hoạt động và chuyển tiếp original bearer. Thêm ví dụ env và runbook TLS/registry/assignments, cập nhật các README hiện hành.

## Kiểm chứng cuối cùng

| Module | Tests pass | Failure / error / skipped |
| --- | ---: | --- |
| Auth | 10 | 0 / 0 / 0 |
| Patient | 294 = 214 unit + 80 integration | 0 / 0 / 0 |
| Appointment | 29 | 0 / 0 / 0 |
| Gateway | 1 | 0 / 0 / 0 |
| Tổng | **334** | **0 / 0 / 0** |

Các lượt verify cuối của từng module đã thành công. Appointment TCP test dùng actual Spring application, Flyway V1–V4 và PostgreSQL tạm riêng: anonymous/inactive/wrong owner bị chặn không ghi; self booking/cancel/reschedule và replay hoạt động; scope STAFF bị thu hồi chặn cả cached replay; list/count chỉ chứa patient trong scope; Basic SERVICE chỉ nhận projection bốn trường. Gateway test chạy route stack thật tới synthetic backend.

Cross-module contract probe có **8 checks PASS**, dùng production Auth producer và Patient/Appointment decoders: SERVICE registry, trusted self mapping, account deactivation, exact scope echo/evidence, STAFF list/cancel scope, cross-patient deny, UUID medical-reference và không lộ reasonForVisit. External IdP/storage là mocks trong probe; provider upstream của các TCP test là doubles. Không gọi đó là real deployment E2E.

`git diff --check` pass. Các log nằm ở `%TEMP%/fclinic-security-auth-final.log`, `fclinic-security-patient-appointment-verify.log`, `fclinic-security-appointment-final.log`, `fclinic-security-verify.log` (Gateway result) và `fclinic-service-contracts-final.log`.

## Cấu hình khi chạy thật

Điền các credential riêng, thiết lập TLS/trust cho Auth, xác nhận APPOINTMENT_SERVICE_SUBJECT là subject thực tế của service account, bật Patient service-integration và Eureka registration khi chạy qua Gateway. STAFF/DOCTOR scope cần assignment được cấp rõ ràng; không có quyền mặc định cho mọi bệnh nhân. Thay đổi assignment configuration cần reload/restart Auth.

Hướng dẫn và ví dụ: [security integration runbook](<C:/Fall 2026/MSS301/clinic-appointment-system/ClinicAppointmentBE/services/appointment-service/docs/integration-readiness/09_SECURITY_INTEGRATION.md>).

Các phát hiện Google login/OTP/public account linking của audit trước, Doctor UUID/published-slot, FE Patient v3 và Notification UUID delivery nằm ngoài đợt sửa này. Không thay đổi các phần đó để tạo kết quả kiểm thử giả.
