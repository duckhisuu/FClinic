# Demo luồng tài khoản mới → đặt lịch

Mở ứng dụng tại `http://127.0.0.1:5173`. Hạ tầng Docker và các service backend cần đang chạy. Nếu cần khởi động lại, xem [runbook](../integration/LOCAL_RUNBOOK.md).

## Kịch bản trình bày

| Bước | Thao tác | Kết quả cần trình bày |
| --- | --- | --- |
| 1 | Đăng ký một tài khoản bệnh nhân mới, dùng username/email chưa có | Đăng nhập vào ứng dụng và mở phần khởi tạo hồ sơ |
| 2 | Nhập họ tên, số điện thoại, ngày sinh, giới tính và địa chỉ; bấm lưu | Hồ sơ được liên kết với đúng tài khoản; chuyển sang phần chọn bác sĩ |
| 3 | Chọn bác sĩ, ngày và một giờ còn trống; xác nhận đặt lịch | Hiện mã hẹn, thông tin người khám và giờ khám đã lưu |
| 4 | Mở “Lịch hẹn của tôi”, đổi sang một giờ khác | Lịch hẹn cập nhật giờ mới và có lịch sử thay đổi |
| 5 | Hủy lịch hẹn, rồi xem lại giờ khám | Lịch hẹn có trạng thái đã hủy; giờ được trả lại danh sách trống |

Nếu tài khoản đang dùng đã có hồ sơ, bắt đầu từ bước 3. Nếu có tiến trình khởi tạo đang chờ, bấm “Tải lại tiến trình” rồi “Tiếp tục khởi tạo hồ sơ” để dùng lại tiến trình đó.

## Các ý giải thích ngắn

- Auth xác định tài khoản; Patient quản lý hồ sơ; Doctor công bố ca khám; Appointment quyết định giờ nào đang bị chiếm.
- Trình duyệt gửi thông tin hồ sơ, không tự chọn UUID hoặc tự nhận quyền sở hữu hồ sơ.
- Tiến trình onboarding được lưu trên server. Gửi lại cùng yêu cầu sẽ tiếp tục cùng hồ sơ thay vì tạo trùng.
- Slot trùng bị từ chối bằng ràng buộc database. Phí khám âm bị từ chối khi nhập qua API.
- Docker chạy các database trong container; các file migration đi cùng code để từng máy cập nhật cấu trúc DB của mình.

## Tài liệu khi cần giải thích thêm

- [Hợp đồng, migration và credential onboarding](../integration/TRUSTED_ONBOARDING.md)
- [Báo cáo kiểm thử luồng đã sửa](../reviews/2026-11-10-booking-flow-fix.md)
- [Các phần còn ngoài phạm vi](../integration/REMAINING_ISSUES.md)

Các báo cáo merge/bàn giao cũ nằm trong `docs/archive/root-reports/`. Log `.codex-*.log` tạm ở thư mục gốc đã được dọn. Cấu hình hiển thị của VS Code ẩn thư mục lưu trữ, `Report` và `.runtime`; dữ liệu trong các thư mục đó vẫn còn.
