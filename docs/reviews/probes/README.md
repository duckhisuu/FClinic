# Logic review probes — 2026-11-10

Các script này **tái hiện lỗi còn tồn tại**, không chứng minh hệ thống đã được sửa. Không cần tài khoản thật; không ghi dữ liệu deployment. Auth dùng mock tại port; Doctor chạy server HTTP thật với PostgreSQL riêng rồi dừng.

Sau khi chạy Maven tests (Java 21) và cài frontend dependencies:

```powershell
& ./docs/reviews/probes/Run-LogicProbes.ps1 -JavaHome 'C:\path\to\jdk-21'
& ./docs/reviews/probes/Run-FrontendLogicProbes.ps1
```

Cả hai nhận `-RepositoryRoot` tùy chọn để kiểm tra một bản snapshot riêng. Backend runner lấy classpath từ Surefire XML; nếu source đã đổi thì phải build/test lại trước.

Backend in `REPRODUCED ...` khi hành vi lỗi đã được xác nhận. Frontend runner copy tạm file test vào thư mục test của repo đích rồi xóa đúng file đó trong `finally`; từ chối ghi đè file có sẵn. Hai test dùng `it.fails`: invariant đúng hiện thất bại như dự kiến. Sau khi sửa lỗi, chuyển chúng thành regression tests thông thường và cập nhật assertion/fixture phù hợp.

Giữ đường dẫn snapshot ngắn trên Windows vì bộ PostgreSQL nhúng có thể lỗi khi giải nén/khởi tạo ở đường dẫn dài. Báo cáo chính chốt trên `b2ec0aaf384df7644a703f2e883e4cc1d2f5e7ee`.
