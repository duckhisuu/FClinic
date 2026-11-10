# Conflict resolution — Phase 1

## Conflict Git

- Main merge: 0 paths unmerged.
- Datdt merge: 0 paths unmerged.
- git diff --name-only --diff-filter=U rỗng trước mỗi commit; không có thao tác blanket ours/theirs.
- Không truy cập/chỉnh sửa clone Appointment cũ đang có 15 conflicts.

## Hai khác biệt ngữ nghĩa đã review

| Path | Incoming và rủi ro | Quyết định | Evidence |
|---|---|---|---|
| ClinicAppointmentFE/src/App.jsx | datdt thay entry point hiện tại bằng Layout/BookingPage/PatientHistoryPage cũ; mất props actorSubject/patientId và màn Appointment hiện tại | Ghép từng phần: giữ App hiện tại, DoctorDirectory, Booking, AppointmentHistory, NotificationDrawer; thêm DoctorSchedulePage trong tab quản lý demo. onSelectSlot từ màn Doctor chỉ hiện thông báo, không truyền Long/mock IDs sang booking UUID | datdt-incoming-diff.log, datdt-reviewed-diff.log |
| ClinicAppointmentBE/.env.example | datdt thêm mặc định admin/1234 cho credentials Patient | Giữ hai biến credentials explicit như parent main merge. Không đổi cấu hình Patient | protected-services-datdt-diff.log rỗng |

## Các file còn lại trong datdt

- ClinicAppointmentFE/src/pages/DoctorSchedulePage.jsx: nhận implementation mới nhất từ datdt: profile, lịch tuần/tháng, quản lý Doctor/slot.
- ClinicAppointmentFE/src/services/api.js: nhận Doctor API và demo mock của datdt; giữ nguyên các API object Appointment, Patient, Notification từ main. Đây là mock gốc của Doctor UI, không phải Long-to-UUID fallback mới; không chứng nhận hành vi mock là kết quả backend thật.
- ClinicAppointmentFE/src/components/Sidebar.jsx: nhận thay đổi menu của datdt; App tích hợp vẫn dùng sidebar hiện tại để bảo toàn luồng Appointment. Menu Outbox của Sidebar chưa được nối vào App.

## Bảo toàn

Backend datdt không có net change sau review; toàn bộ Patient và Appointment backend/UI giữ nguyên so với main merge. Source Doctor backend không bị sửa kiểu ID. Kiểm chứng: protected-path-verification.json, các reviewed diff và test XML. Chưa tạo cầu nối từ Doctor Long sang Appointment UUID.
