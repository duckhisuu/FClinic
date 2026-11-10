# Tài khoản mới → hồ sơ → đặt lịch

Luồng này thay thế việc React tự tạo Patient rồi gửi một UUID vào endpoint self-link. Endpoint cũ `PUT /api/v1/auth/me/patient-profile` vẫn không cho claim/rebind UUID tùy ý.

## Hợp đồng hiện hành

- `GET /api/v1/auth/me/patient-onboarding`: đọc tiến trình của account đang xác thực, trả `{profile, user}` trong envelope. `profile` là request đã lưu nếu có, `user.patientId` cho biết binding đã hoàn tất.
- `POST /api/v1/auth/me/patient-onboarding`: nhận duy nhất `fullName`, `phoneNumber`, `dateOfBirth`, `gender`, `address`. Email lấy từ account; không nhận `patientId`, `accountId`, role hoặc subject từ trình duyệt. Trả UserResponse sau khi hoàn tất binding.
- `POST /internal/v1/patient-onboarding` thuộc Patient chỉ dành cho Auth với credential riêng. Request chứa reservation do Auth cấp, hash và profile. Response proof phải khớp account UUID, Patient UUID, subject, request hash và trạng thái ACTIVE.

Auth lưu reservation bền vững theo `user_id`, rồi gọi Patient và đồng bộ Keycloak ngoài transaction DB. Patient serialize theo account, tạo profile + mapping + revision trong cùng transaction. Auth khóa account khi hoàn tất liên kết. Cả hai nơi đều có ràng buộc duy nhất bảo vệ concurrent requests.

Retry cùng account và nội dung sẽ tiếp tục cùng Patient UUID. Sau mất response, logout, reload, đổi tab hoặc thiết bị, GET lấy lại request đang chờ từ server. Request khác nội dung khi reservation đã tồn tại trả 409; UI có nút tải lại tiến trình. Nếu Keycloak lỗi, reservation và hồ sơ đã tạo được giữ lại để retry hoàn tất. Không tạo MedicalRecord để kích hoạt đặt lịch.

React không lưu pending Patient UUID hoặc thông tin hồ sơ trong sessionStorage. Kết quả bất đồng bộ phải thuộc đúng account và phiên đăng nhập hiện tại mới được dùng; logout/login lại cùng account cũng tạo phiên mới.

## Cấu hình và nâng schema

Auth và Patient dùng cùng một credential **riêng cho provisioning**:

```properties
AUTH_PATIENT_ONBOARDING_CLIENT_ID=auth-patient-onboarding
AUTH_PATIENT_ONBOARDING_CLIENT_SECRET=<random-secret>
AUTH_PATIENT_ONBOARDING_URI=http://localhost:8081/internal/v1/patient-onboarding
```

URI Auth → Patient chỉ cho HTTP loopback hoặc HTTPS. Ngoài localhost cần TLS và trust store của JVM đúng CA. Không dùng secret introspection/reference cho provisioning. Nếu secret chưa được cấu hình, endpoint nội bộ từ chối và Auth không tạo binding.

Trước khi chạy ứng dụng mới:

1. Auth chạy Flyway `V3__patient_onboarding.sql`: tạo reservation table và unique indexes cho `users.patient_id`/`doctor_id`.
2. Patient UUID DB chạy script additive `services/patient-service/src/main/resources/db/schema/uuid/V002__patient_onboarding.sql`.
3. Doctor chạy `services/doctor-service/src/main/resources/db/schema/V002__doctor_booking_constraints.sql`. Script kiểm tra dữ liệu trùng/âm trước khi thêm constraint. Không xóa/gộp slot hay sửa phí cũ tự động.

`Start-FClinic.ps1` đã bao gồm bước chuẩn bị schema Patient/Doctor và sinh credential provisioning riêng trong `.runtime/settings.json` khi chưa có. Auth migration chạy khi service khởi động. Script chỉ khởi tạo canonical schema khi DB rỗng; legacy schema hoặc dữ liệu xung đột cần được đối chiếu trước. Giữ nguyên dữ liệu và các credentials đang dùng.

Các service đang chạy từ code cũ cần được khởi động lại sau build. Dùng JDK 21 và Maven trong PATH theo [local runbook](LOCAL_RUNBOOK.md):

```powershell
& ./docs/integration/Start-FClinic.ps1 -Build
```

Nếu chạy từng service bằng Maven thay cho runbook, cấp credential hai phía và chạy các migration additive nói trên trước khi restart. Không chạy SQL baseline V001 lên DB đã có dữ liệu.

## Lịch bác sĩ và validation

Lịch làm việc chỉ hiển thị Doctor đang liên kết với account. Ca khám là offer của Doctor; trạng thái chiếm chỗ lấy từ các lịch CONFIRMED trong Appointment, không đọc cờ `Doctor.isBooked` cũ. Màn hình không còn action đặt lịch dành cho bệnh nhân hay callback thiếu handler. Khi không đọc được occupancy, UI hiện lỗi và không đoán là còn trống.

Slot trùng Doctor/ngày/giờ bị DB từ chối, HTTP trả 409 kể cả khi tạo đồng thời. Phí âm và số năm kinh nghiệm âm bị từ chối 400; domain cũng từ chối phí không hữu hạn. Thời lượng slot vẫn là đúng 60 phút.

## Kiểm tra

```powershell
mvn -f ClinicAppointmentBE/pom.xml verify -B -ntp
cd ClinicAppointmentFE
npm test
npm run build
```

Acceptance sau package, với Keycloak trong cấu hình hiện có đang healthy:

```powershell
& ./docs/integration/Test-TrustedOnboarding.ps1 -JavaHome 'C:\path\to\jdk-21'
```

Acceptance chạy các JAR Auth, Patient, Doctor, Appointment thật với bốn DB PostgreSQL tạm và port riêng. Nó dùng Keycloak thật, tạo hai account tổng hợp với nonce riêng, kiểm tra concurrent onboarding, replay, cách ly account, đặt/đổi/hủy và occupancy. Sau đó chỉ xóa các identity do chính lần chạy đó tạo, sau kiểm tra username prefix. Không reset realm/volume, không ghi business DB deployment. Outbox publishing tắt trong acceptance nên không phát email. Kết quả ghi ở `trusted-onboarding-acceptance.json`.

Các lỗi OTP, phân quyền ghi Doctor public và xác minh reminder khi gửi lại trong báo cáo review ban đầu nằm ngoài phạm vi nhóm thay đổi này; không dùng kết quả onboarding để kết luận các mục đó đã được sửa.
