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

Auth/Keycloak thực quyết định identity và role. Patient tự khởi tạo được liên kết tự động sau khi xác minh quyền sở hữu. Hồ sơ do Staff tạo và Doctor UUID dùng liên kết tin cậy qua API ADMIN hiện hành. Không thay ID/role ở frontend. Tài khoản STAFF/DOCTOR cần assignment hữu hạn, có hiệu lực; empty grants trả 403 là đúng. User đọc/lưu qua Patient vẫn được backend kiểm tra độc lập với UI capabilities.

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

Không suy diễn `appointmentId=null` là có quyền: `create` chuẩn bị VALID_VISIT độc lập trước khi `verify` lưu bệnh án. Hồ sơ do Staff tạo vẫn cần trusted assignment cho UUID mới để Staff đọc/sửa sau đó; Patient tự khởi tạo dùng quyền self-read theo identity đã xác minh, xem REMAINING_ISSUES.md.

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


## Latest main + Notification update (2026-11-09)

Start-FClinic.ps1 now idempotently adds only the reviewed Notification audience mapper to the existing Keycloak fclinic-claims scope via Admin API, using credentials from the existing .env. It does not reset/import over the realm. Login again to obtain a token with notification-service audience. Auth HTTPS and Patient trust/hostname validation remain enabled.

Notification V4/V5 are applied by Flyway at service startup. Before this integration they were rehearsed on restored database test copies with a synthetic numeric row (archive=1, active=0, templates=8). Actual source DB had 0 notifications and 8 templates. Backups/test copies remain in ignored .runtime/current PostgreSQL test databases; they are not runtime alternatives. Legacy rows are archived, not assigned guessed UUID owners or replayed. DLQ is now 4 (3 preserved old messages + 1 intentional malformed acceptance event). Do not purge/replay it to make tests pass.

Self-service onboarding now uses the canonical Patient database and an additive owner association. `Start-FClinic.ps1` applies the repeatable Patient V002 schema addition after checking the UUID schema. Auth Flyway V3 enforces unique non-null Patient bindings; reconcile historical duplicates explicitly if this migration fails. Backups were taken before the actual local migration. No profiles, volumes or realms are reset.

Patient `POST /api/v3/patients` serializes initial creation by verified subject. `GET /api/v3/patients/self-profile` recovers only that subject's active initial profile. Auth `PUT /api/v1/auth/me/patient-profile` independently checks this provider with the same bearer before persisting the link. The provider defaults to loopback http://localhost:8081 (current Patient transport); remote targets require HTTPS with normal certificate verification. Configure `auth.patient-service-url` when needed. Auth introspection and WorkScope HTTPS are unchanged.

For a new account: login → fill initial profile → Save & Continue → Doctor Directory. No MedicalRecord is required. Historical unlinked profiles lacking the new owner association require owner-reviewed reconciliation; do not claim them by UUID or create medical records as a workaround.

Run `node docs/integration/Test-SelfOnboarding.cjs` against healthy real services for the new automatic flow. This creates only synthetic accounts and profiles and checks canonical PostgreSQL rows, ownership, retry and session persistence. Private credentials stay in ignored `.runtime/self-onboarding-state.json`. The older onboarding-notification script describes the historical operator-provisioning acceptance, not the new automatic-link gate.
Repeatable test-scope commands (services already healthy; no real medical data):

```powershell
node docs/integration/Test-OnboardingNotification.cjs
node docs/integration/Test-OnboardingNotification.cjs edges
# Optional: republishes only the new test booking event; adds one deliberate poison event to DLQ.
node docs/integration/Test-OnboardingNotification.cjs broker
node docs/integration/Test-PatientFlows.cjs verify
node docs/integration/Test-PatientFlows.cjs negative
```

The scripts retain synthetic resource IDs in ignored .runtime and may replay existing idempotency results on subsequent runs. Do not remove state/DB rows to disguise a failure. The first fresh account/profile/booking run and browser run are recorded in current evidence. Broker mode changes DLQ by design; do not run it merely to refresh a report count. Fixtures/grants expire; use the existing assign/restart procedure above to renew exact finite assignments.

Run every Maven verify/repackage while managed JARs are stopped; run Patient-only verify and contracts before startup as well. Then Start-FClinic.ps1 and the existing Vite command above. Startup was run again successfully after Doctor outage recovery. All 8 health checks passed; the existing Vite listener was verified as Primary via its served source path and reused.

Manual checks at http://127.0.0.1:5173:

1. The retained browser session is synthetic linked Patient A at #history. Inspect the CONFIRMED 11 November booking and its CREATED history.
2. Open Notifications: only A's genuine confirmation/reschedule/cancel messages appear; no admin retry for PATIENT.
3. Browse Doctor slots, select a future available hour, book, then read history/cancel/reschedule with the returned version.
4. Refresh: identity/link persist. Logout: protected UI disappears. A separate unlinked Patient can create and automatically link only its own server-provisioned initial profile.
5. STAFF/DOCTOR use existing synthetic accounts/credentials from the current private local realm configuration, not passwords in this report. Their Patient/clinical actions still require exact finite scope and VALID_VISIT.

See PATIENT_ACCEPTANCE_TESTS.md for current HTTP/browser evidence and REMAINING_ISSUES.md for owner-review blockers. No statement of full LOCAL READY is made.

Final broker snapshot: current `notification.appointment.q` is drained, `notification.appointment.dlq` has 4 preserved messages, and additional `fclinic.appointment.notifications` has 14 buffered event copies in the latest 2026-11-10 snapshot. Do not purge or replay the additional queue; confirm its ownership/retirement first. It is not the current Notification consumer queue.

## Staff reception → Doctor clinical acceptance (2026-11-10)

The retained case and exact IDs are in PATIENT_ACCEPTANCE_TESTS.md. It is open in React as the synthetic linked Patient. Eight canonical Patient endpoints, browser Staff/Doctor editors, read-only Doctor and own Patient readback passed. No Patient/MedicalRecord data is synthetic runtime fallback: all test inputs are explicitly synthetic, but persisted in the actual canonical PostgreSQL databases.

`Test-ReceptionClinical.cjs` is test orchestration only. Its phases preserve private accounts/resource IDs in ignored .runtime/reception-clinical-state.json and append all public checks, including failed attempts, to reception-clinical-evidence.json. Never run it against real medical data. It preserves pre-existing grant entries and takes an assignments.json backup before adding finite 8-hour human grants (all-patients=false). Secrets are not written to reports.

For a fresh test fixture: `prepare` creates permitted synthetic accounts and prepares finite assignment configuration; restart Auth through the existing runbook. Staff creates the profile in React. `scope <PatientUUID>` verifies the exact immutable Staff creator/name/email/active row and unclaimed account binding, uses the existing trusted ADMIN association API, and prepares exact Patient grants; reload Auth. `slots` publishes an actual Doctor offer. Staff selects that Patient/Doctor/slot and books through React. `visit <AppointmentUUID>` verifies actual booking ownership/status and prepares exact VALID_VISIT with appointmentId, visitDate and visitId; reload Auth. Doctor enters those exact IDs/time from the private state and saves/amends the medical record through React. `verify` executes profile/record reads and replacements, pagination, version conflicts and negative authorization checks. `extras` checks no-op/status/self-onboarding/Notification/session behavior. `audit` compares real HTTP data to PostgreSQL and compares cold-restart snapshots.

Reuse the current case without creating another fixture:

```powershell
Set-Location 'D:\Hoc_tap\26_FALL\MSS301\Project_Group\FClinic_Primary'
node docs/integration/Test-ReceptionClinical.cjs audit
# Renewal of the same finite grants, if expired; does not reset accounts/data:
node docs/integration/Test-ReceptionClinical.cjs prepare
# Stop only the verified Primary Auth JAR before Start-FClinic.ps1 reloads assignments.
$authPid = [int](Get-Content .runtime/auth-service.pid)
$authProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$authPid"
$primaryRoot = (Get-Location).Path
if ($authProcess.Name -ne 'java.exe' -or $authProcess.CommandLine -notlike "*$primaryRoot*auth-service-1.0.0-SNAPSHOT.jar*") { throw 'Auth PID ownership mismatch' }
Stop-Process -Id $authPid
& .\docs\integration\Start-FClinic.ps1
```

`outage` is an explicit negative test requiring only the verified Primary Doctor JAR stopped; it expects 503 and unchanged database state. Restore Doctor with Start-FClinic.ps1 immediately afterward. It was exercised successfully and Doctor is running again. Do not stop arbitrary Java processes or replay/delete queues.

Startup now waits for actual Gateway routes after Actuator UP: Auth me 401, Doctor list 200, Patient 401, Appointment 401, Notification me 401. These are read-only probes; 401 is the correct unauthenticated response proving the protected backend is reachable, not an auth bypass. A cold restart demonstrated that Appointment discovery needed 29 attempts even after health UP. The script now waits before declaring ready. Launch Vite only after it finishes.

Appointment status does not automatically become COMPLETED after saving a MedicalRecord because no completion operation is currently published. Do not update PostgreSQL directly to simulate it. Initial Staff-created profile scope and trusted administrative account association are explicit test setup; automatic assignment UI remains pending.
