# Sửa luồng tài khoản mới và lịch bác sĩ — 10/11/2026

Đã sửa trong working tree; chưa commit hoặc triển khai các JAR mới lên business DB đang dùng. Các bài nghiệm thu chạy trên source snapshot của thay đổi, với business DB tạm riêng và Keycloak thật. Không dùng mock identity cho bài end-to-end; các account tổng hợp do bài đó tạo đã được xóa sau khi xác minh đúng nonce.

## Đối chiếu yêu cầu

| Yêu cầu | Thay đổi | Bằng chứng |
| --- | --- | --- |
| Slot trùng | Unique Doctor/ngày/giờ ở DB; API trả 409 khi xung đột, kể cả hai request đồng thời | `DoctorBookingValidationHttpTest.concurrentDuplicateOffersCommitOneSlotAndReturnConflictForTheOther` đạt với HTTP 201 + 409 |
| Phí khám âm | DTO và domain từ chối giá trị âm; domain cũng từ chối phí null/không hữu hạn; migration thêm check DB | `negativeFeeAndExperienceAreRejectedAtHttpBoundary` đạt, hai request 400 |
| Nút chọn ca gây lỗi | Màn hình Doctor chỉ quản lý lịch của Doctor đã liên kết; bỏ action đặt lịch/callback thiếu handler | `doctor-schedule.test.jsx`: tab Doctor mở được và không còn nút gọi `onSelectSlot` |
| Lịch hiển thị sai | Đọc CONFIRMED occupancy từ Appointment, ghép với offer Doctor; không dùng `Doctor.isBooked` cũ | Test component với `isBooked=false` nhưng có appointment; refresh sau hủy chuyển về trống. E2E xác nhận đổi/hủy trả giờ trống đúng |
| Cache onboarding lẫn account | Bỏ pending Patient UUID trên trình duyệt; tải tiến trình từ server; gắn mọi continuation với account và phiên đăng nhập | `onboarding.test.jsx`: bỏ cache cũ, bỏ response muộn của account A sau khi đổi B, phục hồi sau remount |
| Tài khoản mới → đặt lịch với provisioning tin cậy | Auth giữ reservation bền vững; Patient xác minh credential riêng và tạo profile/mapping/revision nguyên tử; Auth kiểm proof ACTIVE rồi đồng bộ Keycloak và bind; unique mapping ngăn nhiều owner | E2E bằng Auth/Patient/Doctor/Appointment JAR thật + Keycloak thật: đăng ký, login, hai request tạo hồ sơ đồng thời, replay, chặn claim UUID, cách ly A/B, đặt/đổi/hủy đều đạt |

## Kết quả cuối

- `mvn verify`: **BUILD SUCCESS**, 313 test Surefire và 80 test integration Failsafe, không failure/error/skip.
- Frontend: **53 test / 11 file đạt**, Vite production build thành công.
- Hợp đồng giữa service: **13 kiểm tra đạt**.
- Trusted onboarding acceptance: **20 kiểm tra đạt**, gồm hai kiểm tra cleanup identity tổng hợp.
- Startup helper: PowerShell parse không lỗi; đã smoke-test Java argument có khoảng trắng trên chính đường dẫn workspace Windows.

Xem [bằng chứng máy đọc được](2026-11-10-booking-flow-fix-evidence.json) và [kết quả E2E](../integration/trusted-onboarding-acceptance.json). Snapshot loại trừ các test bỏ qua bởi Git còn sót từ các checkout khác; số liệu này là bộ test thuộc source bàn giao.

## Hành vi khi có lỗi hoặc retry

- Request public chỉ chứa demographics, không chứa Patient UUID, account UUID hay subject.
- Cùng account + cùng request dùng lại một reservation/Patient UUID qua mất response, reload hoặc đổi thiết bị.
- Request đổi nội dung trong khi reservation đã tồn tại trả 409; UI cho tải lại tiến trình đã lưu.
- Patient không nhận claim một hồ sơ có sẵn không thuộc reservation và không tái kích hoạt profile INACTIVE.
- Credential sai, proof không khớp hoặc provider lỗi không tạo Auth binding.
- Keycloak đồng bộ lỗi giữ nguyên tiến trình; retry xác minh lại Patient rồi tiếp tục trước khi bind.
- Transaction Auth ngắn chỉ giữ khóa khi reserve/bind; HTTP Patient và Keycloak không chạy trong transaction đó.
- Subject khác không được tự nhận diện thành account hiện có qua email/username. Thay đổi này là điều kiện bảo vệ chủ thể của provisioning.

## Áp dụng cho môi trường đang chạy

[TRUSTED_ONBOARDING.md](../integration/TRUSTED_ONBOARDING.md) mô tả endpoint, credential và migration. Auth chạy Flyway V3; Patient cần additive V002; Doctor cần additive V002. `Start-FClinic.ps1 -Build` chuẩn bị schema/credential và restart các JAR do script quản lý, sử dụng JDK 21/Maven trong PATH. Nếu service đang chạy bằng Maven riêng, dừng/restart đúng service sau khi áp dụng migration và cấp credential hai phía.

Migration không xóa/gộp slot, thay phí hoặc chọn lại owner của dữ liệu cũ. Nếu DB hiện có dữ liệu xung đột, migration dừng để đối chiếu; không mở self-link tùy ý để vượt qua lỗi.

Các lỗi OTP, phân quyền ghi Doctor public và reminder stale trong review toàn dự án chưa nằm trong nhóm sửa được yêu cầu lần này. Kết quả trên không khẳng định toàn bộ backlog ban đầu đã được xử lý.
