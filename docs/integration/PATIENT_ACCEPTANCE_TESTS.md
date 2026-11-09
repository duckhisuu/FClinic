# Patient acceptance — 2026-11-09

Kết luận: **Patient flows đã được kiểm thử với provider/PostgreSQL thật; toàn sản phẩm PARTIAL**. Không dùng fake API, mock session hay fixture runtime.

| Flow | Kết quả | Bằng chứng |
|---|---|---|
| 1. Authentication | PASS trong phạm vi login/me/refresh/logout/revocation | runtime-http-evidence.json; browser Staff → logout → Doctor; identity/role thật; logout mất protected UI ngay |
| 2. Profiles | PARTIAL end-to-end onboarding; CRUD theo assignment PASS | Browser tạo FClinic Browser Synthetic; PostgreSQL có row thật; profile hiện hành sửa version 1 → 2 và đọc lại; search/pagination + stale version qua HTTP |
| 3. Medical Records | PASS cho walk-in được cấp VALID_VISIT | React Doctor đọc Patient, tạo record UUID 4987af63-82af-40ca-8441-765a54ff1097, sửa với reason/version 0 → 1, refresh deep link đọc lại; PostgreSQL xác nhận |
| 4. Negative tests | PASS các ca thực thi, PARTIAL tổng ma trận | 401 thiếu/invalid/revoked token; 403 missing exact scope/wrong Patient/wrong Doctor/unauthorized write; 400 UUID; 409 version; Doctor outage 503; DB không đổi |
| 5. Navigation | PASS các tuyến đã thực thi, PARTIAL guard cancellation | Login → role landing → Patient list/detail → records/create/detail/edit; Doctor appointment → Patient; browser Back; refresh deep link; logout/account role switch; confirm unsaved-change đã xuất hiện, nhánh Cancel chưa xác minh trọn vẹn do dialog công cụ |
| 6. Cross-service | Appointment PASS; Notification BLOCKED | 11/11 checks qua Gateway: published 60-minute slots, Staff booking, Patient M2M lookup, Doctor association, self read/reschedule/cancel; Notification schema3 UUID chưa tương thích |

## HTTP và database evidence

- runtime-http-evidence.json: **24/24 PASS** ở lần chạy cuối. Lần đầu cũng đã tạo medical record qua HTTP; lần cuối tái sử dụng record đã lưu, kiểm revoked token sau logout.
- cross-service-http-evidence.json: **11/11 PASS**.
- authorization-negative-evidence.json: **7/7 PASS**. unauthorized-database-evidence.json: hai Patient rows/version/address không đổi sau các request bị từ chối.
- provider-outage-evidence.json: **3/3 PASS** (bao gồm login thật); outage-database-evidence.json: Medical Record count trước/sau **2/2**, unchanged=true. Doctor được khôi phục, health UP.
- runtime-health-evidence.json: Discovery/Auth/Patient/Doctor/Appointment/Notification/Gateway đều HTTP 200, status UP. Auth được gọi bằng CA tin cậy, không bỏ TLS verification.

## Browser evidence

- patient-profile-browser.jpg: profile thật sau React update, version 2.
- medical-record-browser.jpg: bệnh án tạo/sửa qua React và đọc lại sau refresh, version 1, Doctor UUID thật.
- doctor-appointments-browser.jpg: lịch hẹn thật có Patient association.
- Notification drawer hiển thị lỗi UUID contract và Retry, không hiển thị mock hoặc báo đã gửi.
- Browser kết thúc ở login, không để dữ liệu protected hiển thị sau logout.

## Giới hạn xác minh

Expired-token/refused-provider/reference/UUID contracts được kiểm thử tự động; không chờ một JWT thật hết hạn theo đồng hồ browser. Automatic refresh có regression test coalescing/no-loop/account-switch; real refresh API và revoked token đã kiểm thử. Missing Doctor/Appointment provider resource cases chưa được thực thi riêng trên runtime này. Chưa kiểm thử UI Admin CRUD/registration hoặc booking wizard đầy đủ bằng browser; booking mutations đã chạy HTTP thật qua Gateway.

Hồ sơ mới phát sinh UUID chưa có automatic trusted assignment. Lượt nghiệm thu đã dùng cơ chế deployment hiện hành cấp finite scope cho đúng hai Patient tổng hợp; không cấp wildcard. Walk-in có assignment VALID_VISIT độc lập gắn exact Patient/timestamp/visitId; appointmentId=null không tự sinh quyền. Tài khoản/Doctor/Patient được admin liên kết qua API hiện hành.

Khi kiểm stale version nhiều lần, mỗi lượt dùng payload thực sự thay đổi. PUT no-op có thể không làm tăng JPA version; không dùng no-op làm bằng chứng stale-write conflict.
