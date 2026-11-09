# Local runbook — Windows / PowerShell 7

Chỉ chạy trong `D:\Hoc_tap\26_FALL\MSS301\Project_Group\FClinic_Primary`. Java 21, Maven, Node/npm và Docker Desktop Linux Engine phải khả dụng. Không dùng repository gốc hay SafeSync.

## Khởi động

Mở Docker Desktop, sau đó PowerShell 7:

```powershell
Set-Location 'D:\Hoc_tap\26_FALL\MSS301\Project_Group\FClinic_Primary'
java -version
docker info --format '{{.ServerVersion}}'
# Lần đầu: cài frontend dependencies.
Push-Location ClinicAppointmentFE
npm ci
Pop-Location
# -Build dừng đúng các JAR do script quản lý trước khi verify để tránh khóa JAR trên Windows.
& .\docs\integration\Start-FClinic.ps1 -Build
Set-Location ClinicAppointmentFE
npm run dev -- --host 127.0.0.1
```

Mở [FClinic](http://127.0.0.1:5173). Một Vite entrypoint và `/api` proxy tới Gateway :8080. Có thể dùng `VITE_PROXY_TARGET` để chỉ định Gateway hiện hành; không có fake API runtime. Không chạy Vite thứ hai nếu :5173 đã hoạt động.

Script tái sử dụng `ClinicAppointmentBE/docker-compose.yml`; chỉ copy `.env.example` sang `.env` khi chưa có `.env`. Không ghi đè cấu hình hiện có. Credentials provider được sinh riêng biệt và giữ tại `.runtime/settings.json` (ignored), không đưa vào report/Git. Realm/volume Keycloak đang tồn tại được giữ nguyên; import lần đầu không đồng nghĩa cập nhật realm đã tồn tại.

Thứ tự: PostgreSQL/Keycloak/Redis/RabbitMQ/MailHog healthy → Discovery → Auth HTTPS → Doctor → Patient (identity/scope/Doctor/Appointment reference profiles) → Appointment → Notification → Gateway → React. Script chờ `/actuator/health` trả `UP` ở mỗi bước và dừng nếu không đạt.

Patient datasource lấy port/database hiện hành từ `.env` (lượt này :5443/patient_db). Script kiểm tra schema trước: chỉ chạy DDL UUID canonical khi public schema hoàn toàn rỗng. Nếu có schema legacy không phải UUID thì dừng, bảo toàn dữ liệu; cần mapping/migration do owner xác nhận. Không tạo database/schema thay thế.

## Cổng và health

| Thành phần | URL / cổng |
|---|---|
| React | http://127.0.0.1:5173 |
| Gateway | http://localhost:8080/actuator/health |
| Discovery | http://localhost:8761 |
| Auth | https://localhost:8085/actuator/health |
| Patient | http://localhost:8081/actuator/health |
| Doctor | http://localhost:8082/actuator/health |
| Appointment | http://localhost:8083/actuator/health |
| Notification | http://localhost:8084/actuator/health |
| Keycloak | http://localhost:8180/realms/fclinic/.well-known/openid-configuration |
| PostgreSQL | Auth 5438, Keycloak 5439, Patient 5443, Doctor 5434, Appointment 5435, Notification 5436 |
| RabbitMQ / Redis | 5672 / 6379 |
| RabbitMQ console / MailHog | http://localhost:15672 / http://localhost:8025 |

## TLS và quyền

Auth phục vụ HTTPS bằng certificate có SAN localhost/127.0.0.1. Script tạo PKCS12 trust store, cấu hình Patient introspection/admin/clinical clients, Appointment và JVM/Gateway tin đúng certificate. Không bỏ xác minh chứng chỉ/hostname. Certificate chỉ được tin trong các process đã cấu hình, không thay hệ thống trust của Windows. Kiểm tra:

```powershell
curl.exe --fail --cacert .runtime/localhost.pem https://localhost:8085/actuator/health
```

Auth/Keycloak thực quyết định identity và role. Account ↔ Patient/Doctor UUID phải được admin liên kết qua API hiện hành. Không thay ID/role ở frontend. Tài khoản STAFF/DOCTOR cần assignment hữu hạn, có hiệu lực; empty grants trả 403 là đúng. User đọc/lưu qua Patient vẫn được backend kiểm tra độc lập với UI capabilities.

## Nghiệm thu tổng hợp có thể lặp lại

Script dưới chỉ dùng tài khoản tổng hợp có sẵn trong realm import, đọc credentials từ cấu hình hiện hành, không in token/password. Không dùng trên tài khoản/dữ liệu thật. Grant chỉ tồn tại cho exact Patient UUID, hết hạn sau 8 giờ; walk-in dùng exact visitDate/visitId riêng.

```powershell
node docs/integration/Test-PatientFlows.cjs assign
# Restart Auth để nạp assignment deployment hiện hành:
$authPid = [int](Get-Content .runtime/auth-service.pid)
Stop-Process -Id $authPid
& .\docs\integration\Start-FClinic.ps1
node docs/integration/Test-PatientFlows.cjs create
# Sau khi tạo profile và trusted binding, nạp finite Patient/visit assignment:
$authPid = [int](Get-Content .runtime/auth-service.pid)
Stop-Process -Id $authPid
& .\docs\integration\Start-FClinic.ps1
node docs/integration/Test-PatientFlows.cjs verify
node docs/integration/Test-PatientFlows.cjs cross
node docs/integration/Test-PatientFlows.cjs negative
```

Không suy diễn `appointmentId=null` là có quyền: `create` chuẩn bị VALID_VISIT độc lập trước khi `verify` lưu bệnh án. Tạo profile mới trong UI vẫn cần trusted assignment cho UUID mới để đọc/sửa sau đó; đây là giới hạn onboarding hiện hành, xem REMAINING_ISSUES.md.

## Tests và dừng

Trước Maven verify/repackage, dừng JAR để tránh file lock:

```powershell
& .\docs\integration\Stop-FClinic.ps1
mvn -f ClinicAppointmentBE/pom.xml verify -B -ntp
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp
& .\ClinicAppointmentBE\security-checks\Run-Contracts.ps1
Push-Location ClinicAppointmentFE
npm test
npm run build
Pop-Location
```

Stop script kiểm tra PID + command line thuộc Primary; không dừng service khác và không xóa dữ liệu. Hạ tầng giữ nguyên; nếu cần dừng container dùng `docker compose -f ClinicAppointmentBE/docker-compose.yml stop`. Không dùng `down -v`, reset realm, xóa migration hoặc xóa volumes. Log runtime/PID nằm trong `.runtime/`; build logs ở `docs/integration/*.log` (ignored).

UI clinical acceptance: xem uiVisitDate trong .runtime/acceptance-state.json và nhập đúng thời điểm UTC+07 của visit được cấp. Script create sinh thêm một visit theo độ chính xác phút của form; không bỏ VALID_VISIT. Provider outage test chỉ chạy sau khi dừng Doctor do runbook quản lý, rồi phải khởi động lại bằng Start-FClinic.ps1.
