# Post-sync test results

Thực thi tại `D:/Hoc_tap/26_FALL/MSS301/Project_Group/FClinic_Primary`, source `121ccc6fc9934ce684f024534d715a6629d5d973`; không dùng Maven/npm outputs từ SafeSync để thay cho kiểm chứng mới.

| Gate | Command | Kết quả |
|---|---|---|
| Patient | mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp | PASS, exit 0, 293 tests |
| Full reactor | mvn -f ClinicAppointmentBE/pom.xml verify -B -ntp | PASS, exit 0, 314 tests; 9 projects SUCCESS |
| Dependencies | npm ci trong ClinicAppointmentFE | PASS, exit 0 |
| React | npm run build trong ClinicAppointmentFE | PASS, exit 0 |

## Full reactor XML

| Module | Report | Tests | Failures | Errors | Skipped |
|---|---|---:|---:|---:|---:|
| notification-service | surefire-reports | 21 | 0 | 0 | 0 |
| patient-service | failsafe-reports | 80 | 0 | 0 | 0 |
| patient-service | surefire-reports | 213 | 0 | 0 | 0 |

Patient isolated verify cũng có 213 surefire + 80 failsafe = 293. Giữ toàn bộ historical tests, không skip/disable/delete. Doctor và Appointment không có tests trong source remote này; reactor kiểm compile/package của chúng. Chưa chạy genuine Auth/WorkScope/Doctor clinical E2E.

## Source/Git gates

Ancestry main+datdt+bao đủ; no diff tracked source so với 121ccc6 trước test; tất cả Patient source khớp tree; Doctor UI blob đúng datdt; Appointment UI tree giữ nguyên; 0 unmerged Git paths. Backup SHA256 tất cả file khớp, original full manifest before/after khớp.

## Evidence

promotion-evidence/primary-{patient,reactor,npm-ci,react-build}.log; primary-tests.json có timestamps/exit codes; patient-xml và reactor-xml; primary-test-counts.json. Historical Phase 1 ledger và 136 checksummed items đã được xác minh lại trước promotion; xem preflight/phase1-evidence-validation.json (số lượng chính xác theo filesVerified).

Môi trường: Windows, Java 21.0.8, Maven 3.10.0, Node 24.15.0, npm 11.12.1. Npm ci có advisory từ dependency tree hiện có; không chạy audit fix/đổi lockfile trong promotion.
