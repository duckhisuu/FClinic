# Appointment React

Module React JavaScript trong `src/appointment`; App chỉ nối các màn hình Appointment. API riêng tại `api.js`, không thay adapter dùng chung hoặc dùng mock fallback của màn hình cũ. Các file pages/components cũ đã phục hồi nguyên trạng.

Luồng: chọn bác sĩ và ngày theo tuần/tháng → chọn slot 60 phút → chọn hồ sơ Patient đã tồn tại → xác nhận → đọc lại lịch đã lưu. Quản lý có phân trang, lọc trạng thái, chi tiết/history, đổi giờ cùng bác sĩ, hủy có version. Chọn giờ không tự submit form. Không tạo/sửa Patient, Doctor, tài khoản hay quyền.

Không có màn login, nhập token, tự chọn vai trò hoặc logic security mới. Host có thể truyền patientId/actorSubject đã có vào App: patientId phục vụ hiển thị/lọc, actorSubject chỉ nhóm lệnh chờ trong trình duyệt; chúng không cấp quyền cho API. Cơ chế security hiện có của hệ thống đảm nhiệm phiên và quyền.

Idempotency-Key và nguyên payload được giữ trong sessionStorage khi chưa rõ kết quả (mất mạng, lỗi 5xx, thao tác đang xử lý); thử lại dùng đúng lệnh cũ, không tự tạo lịch lần hai. Có chặn nhấn gửi lặp. Nếu browser không cho lưu mã yêu cầu, không gửi mutation. Lỗi slot/version hiển thị rõ, tải lại trước thao tác tiếp. Không khẳng định email đã được gửi.

Chạy từ ClinicAppointmentFE:

```sh
npm ci
npm run dev
npm run build
```

Mọi request API đi theo luồng FE → API Gateway → service. Khi phát triển, Vite proxy toàn bộ `/api` tới Gateway `http://localhost:8080`; dùng `VITE_PROXY_TARGET` để đổi địa chỉ Gateway. Không cấu hình proxy riêng tới từng service. Khi triển khai, reverse proxy `/api` tới Gateway hoặc đặt `VITE_API_GATEWAY_URL` là origin của Gateway khi build FE (không thêm `/api`); không đặt URL service vào biến này. Gateway đã có route cho Appointment v2, Patient v3, Doctor, Auth và Notification.

API/provider phải chạy thật và dùng hợp đồng response của Report. Provider lỗi sẽ hiện lỗi, không tạo dữ liệu demo. Test/evidence chỉ chứa dữ liệu tổng hợp và được gitignore.
