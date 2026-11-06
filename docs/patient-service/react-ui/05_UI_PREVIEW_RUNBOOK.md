# Mở Patient UI Preview

Workspace bắt buộc: `D:\Hoc_tap\26_FALL\MSS301\Project_Group\FClinic_Primary`. Branch `feature/patient-react-ui`. Không chạy trong original hoặc SafeSync.

## Development

```powershell
Set-Location -LiteralPath 'D:\Hoc_tap\26_FALL\MSS301\Project_Group\FClinic_Primary'
git branch --show-current
Set-Location -LiteralPath ClinicAppointmentFE
# Dừng Vite bằng Ctrl+C trước npm ci trên Windows nếu server đang chạy.
npm ci
npm test
npm run build
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

Mở **http://127.0.0.1:5173/?patientPreview=1**. Development flag chủ động chọn Patient Preview. Không cần backend, DB, tài khoản hay token. Banner luôn nhắc “UI Preview — Dữ liệu minh họa, không lưu vào hệ thống thật.”

Nếu port 5173 đang được server của chính workspace này sử dụng, dùng URL đang chạy; không dừng process project khác. Node 24/npm 11 là môi trường đã kiểm thử. Dữ liệu chỉ sống trong bộ nhớ module; refresh hoặc rời tab Patient rồi mở lại sẽ khởi tạo fixtures mới. Không lưu vào PostgreSQL/localStorage.

## Mở từng màn hình

1. **MH01:** tab Quản lý bệnh nhân. Tìm tên, hoặc chọn Điện thoại và nhập số đầy đủ; bấm Tìm kiếm. Đổi sort/page-size/page. Dùng dropdown tình huống để xem trống/lỗi/tải chậm.
2. **MH02:** bấm tên bệnh nhân hoặc mũi tên mở hồ sơ. Tạo bệnh nhân → nhập họ tên/điện thoại → Tạo hồ sơ. Chỉnh sửa hồ sơ để thử complete PUT/version; thử status INACTIVE có xác nhận.
3. **MH03:** tìm **Nguyễn Minh An** (bản không có hậu tố minh họa), mở profile → Xem bệnh án. Bệnh nhân synthetic này có sẵn 14 records để thử pagination; các bệnh nhân khác ban đầu chưa có bệnh án. Mở một bệnh án để xem đầy đủ nội dung/metadata/UUID.
4. **MH04:** Tạo bệnh án → nhập symptoms/diagnosis, giữ Doctor UUID synthetic hoặc thử validation; appointment có thể để trống trong mô phỏng. Sau lưu, mở Chỉnh sửa bệnh án, thay nội dung và nhập Lý do chỉnh sửa. Version lấy từ adapter sau khi lưu, không tự tăng trong form.
5. **Conflict:** chọn Xung đột khi lưu trước khi submit; xem 409/reload, sau đó chuyển về Bình thường. Preview giả lập kết quả UI, không xác nhận clinical authorization/VALID_VISIT thật.

## Live API

Bấm **Live API** trong module development hoặc mở URL không có flag rồi chọn tab Patient. Không nhập token vào source, form hoặc URL. Khi chưa có host session, UI hiển thị chưa kết nối và khóa tạo/cập nhật; không fallback fixtures. Clinical mutations luôn bị khóa ở phase này.

- Dev proxy đã thêm `/api/v3/patients` tới `http://localhost:8081`; có thể cấu hình `VITE_PATIENT_PROXY_TARGET` trong môi trường development rồi restart Vite.
- Gateway origin khác dùng `VITE_API_GATEWAY_URL` lúc chạy/build; không sửa Gateway trong phase UI.
- Host tương lai truyền prop `patientSession` vào App: getter token từ session đã xác minh và hints quyền resource; xem 01_UI_ARCHITECTURE.md. Backend vẫn kiểm quyền cuối cùng.
- Profile Live cần Auth + administrative WorkScope; read medical cần quyền Patient self/Doctor READ; create/update medical cần trusted Doctor identity + clinical authority/VALID_VISIT. Các providers này vẫn DEFERRED.

## Production preview

```powershell
npm run build
npm run preview -- --host 127.0.0.1 --port 4173 --strictPort
```

http://127.0.0.1:4173/ là production build, **không** phải synthetic UI Preview. Query `?patientPreview=1` bị bỏ qua, không có nút bật fixtures. Chọn Patient để xem Live disconnected state khi chưa có session. Không dùng production preview như bằng chứng genuine Auth/WorkScope E2E.

Ảnh: [danh sách laptop](screenshots/patient-preview-final.jpg), [desktop](screenshots/mh01-1920x1080.jpg), [mobile](screenshots/mh01-mobile-390x844.jpg), [profile](screenshots/mh02-profile-1366x768.jpg), [editor](screenshots/mh04-editor-1366x768.jpg).
