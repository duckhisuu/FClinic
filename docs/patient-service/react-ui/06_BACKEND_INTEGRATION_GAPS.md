# Các phần backend còn deferred

**LIVE API BINDING: PARTIAL.** Tám endpoint v3 đã audit và bind, request/envelope/HTTP errors đã test với doubles, proxy đã cấu hình. Chưa có genuine session/provider credentials được cung cấp để kiểm runtime Live reads/writes. Không tuyên bố actual Patient medical E2E PASS.

| Phần | Trạng thái | Điều kiện trước kết nối thật |
|---|---|---|
| Patient profile v3 | UI/binding implemented | Host session getter Bearer đã xác minh; STAFF administrative WorkScope hoặc quyền read theo policy |
| Medical read | UI/binding implemented | Patient tự đọc hoặc Doctor clinical READ; backend quyết định |
| Medical create/update | Preview implemented; Live disabled | Doctor identity/binding, clinical CREATE/UPDATE, author equality và genuine VALID_VISIT phù hợp thao tác |
| Doctor | DEFERRED, giữ Long | Patient medical v3 hiện vẫn yêu cầu UUID; cần quyết định/versioned Patient-owned backend contract sau, không tự map/cast/hash Long↔UUID |
| Appointment | DEFERRED | Giữ Appointment UUID và reference hiện tại; null appointment không mặc định cho walk-in |
| Notification | DEFERRED | Giữ UI gốc, không thêm kết nối Patient mới |
| Auth/WorkScope | DEFERRED | Không fake login, role/claims/grants. Host quyền chỉ là UI hint; backend không bị bypass |

Patient Backend, Doctor/Appointment/Notification production code, Gateway và migrations không sửa. Các thay đổi Long Doctor dang dở ở SafeSync không được nhập. Doctor UUID hiển thị trong bệnh án là giá trị contract Patient response hiện tại, không phải tên bác sĩ hay lời khẳng định Doctor Service đã đổi UUID.

## Bước tích hợp tiếp theo

1. Hoàn thiện cơ chế session của host, truyền token getter/hints đã xác minh, test 401/403/expired token thật; không nhập token thủ công vào UI.
2. Xác minh profile CRUD qua v3 với Auth/WorkScope thật và version/revision PostgreSQL.
3. Chốt Patient clinical contract cho Doctor Long bằng version/migration an toàn trong scope backend riêng; bảo toàn dữ liệu/contract v3. Không sửa Doctor sang UUID.
4. Xác nhận walk-in authority cụ thể trước khi mở create với appointment null; giữ fail-closed.
5. Sau provider contract approval mới đổi Live clinical gating và chạy genuine authenticated E2E. Không dùng kết quả Preview hay fetch doubles thay evidence runtime.
