# Phân công bác sĩ theo lịch hẹn

Khi bệnh nhân đặt lịch thành công, bác sĩ được chọn trong lịch hẹn có quyền tra cứu hồ sơ và đọc bệnh án của bệnh nhân đó. Auth lấy danh sách từ Appointment bằng credential riêng, còn Patient kiểm tra quyền cho từng yêu cầu.

Nguồn dữ liệu là quan hệ `appointments.doctor_id` / `patient_id` đã được lưu trong database, với trạng thái `CONFIRMED` hoặc `COMPLETED`. Không có UUID tài khoản hay bệnh nhân cố định trong mã cấp quyền, không có thời hạn demo 8 giờ. Lịch `CANCELLED` và `NO_SHOW` không cấp quyền. Nếu vẫn còn một lịch hợp lệ khác với cùng bác sĩ, quyền đọc được giữ lại. Lịch đã khám xong được giữ để bác sĩ theo dõi hồ sơ.

Endpoint nội bộ: `GET /internal/v1/doctor-patient-assignments/{doctorId}` trên Appointment. Chỉ credential Basic `AUTH_APPOINTMENT_ASSIGNMENTS_CLIENT_ID` / `AUTH_APPOINTMENT_ASSIGNMENTS_CLIENT_SECRET` được dùng cho endpoint này; Bearer người dùng và credential tham chiếu khác bị từ chối. Không công khai endpoint qua Gateway. Giới hạn 1.000 bệnh nhân phân biệt mỗi bác sĩ theo hợp đồng Patient hiện hành; vượt giới hạn trả 503, không trả danh sách bị cắt.

Cấu hình hai service cùng một secret riêng trong `.env`:

```properties
AUTH_APPOINTMENT_ASSIGNMENTS_URI=http://localhost:8083/internal/v1/doctor-patient-assignments
AUTH_APPOINTMENT_ASSIGNMENTS_CLIENT_ID=auth-doctor-patient-assignments
AUTH_APPOINTMENT_ASSIGNMENTS_CLIENT_SECRET=<secret-rieng>
```

URI ngoài loopback phải dùng HTTPS. Lỗi kết nối hoặc phản hồi sai không cấp quyền; hệ thống trả lỗi dịch vụ phụ thuộc. Quyền ghi bệnh án và xác minh lần khám tiếp tục theo các policy hiện hành.

Nếu dùng Auth HTTP cục bộ, Patient vẫn cần kết nối TLS để kiểm tra scope. Trong PowerShell 7 tại thư mục gốc:

```powershell
& .\docs\integration\Enable-LocalPatientScopes.ps1
```

Script tạo kết nối TLS loopback tại `127.0.0.1:8445` chỉ cho `POST /api/v1/auth/scopes`, cấu hình trust store riêng của Patient và sinh secret Auth/Appointment nếu chưa có. Script thay import cấu hình demo cũ bằng cấu hình kết nối không chứa grant hay UUID nghiệp vụ. Khởi động lại Auth, Appointment và Patient sau lần cấu hình đầu tiên; chạy lại script để bật TLS endpoint sau khi khởi động lại máy. Start-FClinic.ps1 cũng chuẩn bị credential Auth/Appointment trong cấu hình runtime riêng.

Kiểm tra: đặt lịch bằng tài khoản bệnh nhân, đăng nhập bác sĩ được chọn và mở **Hồ sơ & bệnh án**. Hồ sơ xuất hiện ngay sau khi booking được commit. Bác sĩ khác không được xem. Hủy lịch cuối cùng rồi tải lại danh sách: hồ sơ không còn trong phạm vi của bác sĩ. Không cần restart service khi thêm/hủy lịch.
