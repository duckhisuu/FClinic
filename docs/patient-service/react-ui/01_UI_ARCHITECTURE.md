# Kiến trúc Patient React UI

Triển khai tại FClinic_Primary, branch `feature/patient-react-ui`, base `b14fa8af0ab8d3110eeb31356a4859acfb11839d`. Backend source baseline `121ccc6` giữ nguyên. Ngày hoàn tất kiểm chứng: 07/11/2026, Asia/Saigon.

## Các lớp

- `src/patient/PatientModule.jsx`: mode boundary, điều hướng nội bộ, guard khi có thay đổi chưa lưu/đang submit, notice sau acknowledgment. Không tạo một React app mới.
- `api/patientApi.js`: tám endpoint v3, Bearer từ host session, strict envelope/shape/UUID ownership, allowlist request fields, timeout 10 giây, AbortSignal. Không retry mutation hoặc fallback mock.
- `model.js`: enum, chuẩn hóa điện thoại, Unicode/code-point limits, UUIDv4, DOB và clinical datetime UTC+07, complete representation builders. Version chỉ lấy từ response đã đọc; từ chối integer không an toàn trong JavaScript.
- `hooks/`: đọc bất đồng bộ bằng AbortController + sequence guard; dữ liệu request cũ không ghi đè request mới. `useEditor` khóa duplicate submit, field errors, dirty/reset và kết quả lưu chưa xác định.
- `components/`: form profile/clinical, heading, state, badge, pagination, metadata. CSS Patient có prefix `pt-`.
- `pages/`: danh sách/profile, danh sách/detail bệnh án. MH04 dùng form chung create/edit.
- `preview/`: fixtures tổng hợp và adapter bộ nhớ; dynamic import chỉ khi development + user chủ động bật Preview. Không fetch, không localStorage, không mang Preview data sang Live.

## Session và quyền

App nhận `patientSession` từ host có cơ chế xác minh riêng, mặc định null. Interface tương lai:

```ts
// Interface mô tả, không phải implementation của Auth hoặc capability fixtures.
interface PatientSession {
  getAccessToken(): Promise<string | null>;
  permissions: {
    createPatient: boolean;
    canUpdatePatient(patientUuid: string): boolean;
  };
}
```

Đây là UI hints, không thay thế authorization của PatientPolicy. Không decode JWT để tự tạo role, không nhập token, không bật credential giả. Auth/WorkScope implementation vẫn DEFERRED. Live profile mutations mặc định khóa nếu thiếu capability; Live clinical mutations luôn khóa trong phase này. Protected Doctor reference `/2`, VALID_VISIT và danh tính clinical chưa được kết nối.

## Preview boundary

Mặc định Live. Development entry `/?patientPreview=1` chọn tab Patient và Preview; hoặc người dùng bấm UI Preview trong development. Production không render switch Preview và bỏ qua query flag; fixture module không có trong production bundle. Banner Preview hiển thị trên mọi screen, sticky khi cuộn. Fixture có 24 patients, 14 bệnh án ban đầu cho Nguyễn Minh An, toàn bộ synthetic. Refresh hoặc unmount module xóa thay đổi trong bộ nhớ; đổi Live không gửi dữ liệu Preview đi.

## Shared integration

App chỉ thêm import, Patient tab/branch render và dirty-navigation guard. DoctorDirectory, DoctorSchedulePage, Booking, AppointmentHistory, NotificationDrawer và services/api.js giữ nguyên. Thêm dev proxy `/api/v3/patients` tới `VITE_PATIENT_PROXY_TARGET` hoặc localhost:8081; không sửa Gateway. Mobile sidebar chuyển sang cuộn ngang để năm menu không đè chữ. Không sửa backend, migrations hay source SafeSync.
