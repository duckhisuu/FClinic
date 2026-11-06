# Test results — Phase 1

Môi trường: Windows 11 amd64, Maven 3.10.0, Java 21.0.8, Node 24.15.0, npm 11.12.1. Chạy trong clone mới, không chạy trong original. Tất cả gate dùng test mặc định; không skip/delete/disable test và không chỉnh POM để đổi kết quả.

## Gate commands

| Stage | Command | Kết quả | Log |
|---|---|---|---|
| main trước commit | mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp | PASS, exit 0 | main-patient.log |
| main trước commit | mvn -f ClinicAppointmentBE/pom.xml verify -B -ntp | PASS, exit 0 | main-reactor.log |
| datdt sau review trước commit | mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp | PASS, exit 0 | datdt-patient.log |
| datdt sau review trước commit | mvn -f ClinicAppointmentBE/pom.xml verify -B -ntp | PASS, exit 0 | datdt-reactor.log |
| datdt, ClinicAppointmentFE | npm ci | PASS, exit 0 | datdt-npm-ci.log |
| datdt, ClinicAppointmentFE | npm run build | PASS, exit 0 | datdt-frontend-build.log |

## XML test counts sau full reactor

| Module | Report | Tests | Failures | Errors | Skipped |
|---|---|---:|---:|---:|---:|
| notification-service | surefire-reports | 21 | 0 | 0 | 0 |
| patient-service | failsafe-reports | 80 | 0 | 0 | 0 |
| patient-service | surefire-reports | 213 | 0 | 0 | 0 |

Tổng reactor: 314 tests. Patient: 213 surefire + 80 failsafe = **293 PASS**, khớp baseline. Các stage main/datdt có cùng test counts. Maven báo “No tests to run” ở cả Doctor và Appointment trong source remote này; compile/package SUCCESS không thay thế test runtime/contract của hai service. Reactor có 9 projects SUCCESS. JSON thời gian/exit code: main-tests.json, datdt-tests.json. XML được lưu ở main-xml/ và datdt-xml/; test-counts JSON lưu phép cộng.

## Review bổ sung

- Không có Git conflict chưa resolve.
- Datdt không đổi toàn bộ backend hoặc src/appointment; appointmentApi/patientApi/notificationApi objects giữ nguyên.
- Doctor service tree remote/sau merge đồng nhất.
- Original manifest, HEAD/index/status/refs trước/sau giống hệt.

## Giới hạn gate

Không chạy profile real-provider-contracts, không chạy demo live và không chứng nhận end-to-end Patient–Doctor–Appointment. Doctor numeric contract vẫn không tương thích UUID. Các tests mặc định xanh và UI build xanh không gỡ BLOCKED này. Frontend không có script test riêng; đã kiểm tra build và source preservation. Logs npm/Maven nguyên bản được giữ, gồm warning từ upstream/dependencies.

Lưu ý từ npm ci: npm báo 5 vulnerabilities (3 moderate, 2 high). Không chạy audit fix hoặc đổi package-lock trong phạm vi merge này.
