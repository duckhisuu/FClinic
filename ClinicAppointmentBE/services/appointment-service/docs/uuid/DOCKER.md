# Appointment chạy bằng Docker

Chỉ bổ sung cách đóng gói/chạy Appointment. Không sửa compose hạ tầng, cấu hình Patient/Doctor/Notification/Gateway hoặc security. Image dùng JAR đã build, chạy UID/GID 10001 và giới hạn RAM 512 MiB trong Compose. Build context chỉ chứa JAR; env/report/test không được đưa vào image.

## Build và chạy

Từ root repository, dùng Java 21 và Maven:

```powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/appointment-service -am clean package -Dmaven.test.skip=true
Copy-Item ClinicAppointmentBE/services/appointment-service/.env.example ClinicAppointmentBE/services/appointment-service/.env.docker.local
```

Điền DB/broker credentials hiện có và SERVICE token thật vào `.env.docker.local` đã gitignore. Không commit env thật. Khởi động hạ tầng do nhóm quản lý trước; mặc định network của compose hạ tầng là `clinicappointmentbe_fclinic-network`. Nếu tên khác, đặt `APPOINTMENT_DOCKER_NETWORK` đúng network đó.

```powershell
docker compose --env-file ClinicAppointmentBE/services/appointment-service/.env.docker.local -f ClinicAppointmentBE/services/appointment-service/docker-compose.yml up -d --build
curl.exe http://127.0.0.1:8083/actuator/health
docker compose --env-file ClinicAppointmentBE/services/appointment-service/.env.docker.local -f ClinicAppointmentBE/services/appointment-service/docker-compose.yml logs appointment-service
```

Compose này chỉ tạo Appointment, không tự khởi động hoặc reset DB/service của người khác. Luôn build lại JAR trước image để không chạy source cũ. Migration V1–V4 chạy trên DB Appointment đã chọn; cần hoàn tất mapping/cutover trong [README](README.md) trước khi trỏ vào DB cũ. Không dùng lệnh `down -v` với hạ tầng đang có dữ liệu.

## Địa chỉ trong container

| Thành phần Appointment gọi | Mặc định Docker |
|---|---|
| PostgreSQL Appointment | `postgres-appointment:5432` |
| Patient | `http://patient-service:8081` + `/api/v3/patients/{uuidv4}` |
| Doctor | `http://doctor-service:8082` + contract UUIDv4 đã bàn giao |
| RabbitMQ | `rabbitmq:5672` |
| Eureka, khi bật | `http://discovery-server:8761/eureka/` |

Tên service phải có trong cùng network, hoặc đặt biến URL tương ứng tới địa chỉ thực. Không dùng cổng host 5435 để kết nối PostgreSQL từ container. `localhost` bên trong container là chính container đó. Nếu chạy provider bằng JVM trên Windows, có thể dùng base URL `http://host.docker.internal:<port>`.

API công bố ở `127.0.0.1:${APPOINTMENT_HTTP_PORT:-8083}` trên máy chủ. Client Patient cần collection URI `http://<appointment-host>:8083/api/v2/appointments`, UUIDv4 và header kỳ vọng `patient-appointment-medical-reference/2`.

## Ranh giới của các nhóm khác

- Patient đã chuyển profile sang v3 và dùng `/2` reference. Client HTTP của Patient hiện chỉ chấp nhận loopback; owner Patient phải cho phép hostname Docker để gọi giữa hai container độc lập. Appointment không thay đổi policy của Patient và không thêm route `/internal/v1/appointments` trùng chức năng.
- Bearer SERVICE credential phải được hệ thống hiện có xác thực/cấp quyền. Eureka chỉ phân giải vị trí service. Không có token/authority thật thì Patient có thể trả503; Appointment giữ lỗi dependency, không giả thành thành công hay bệnh nhân không tồn tại.
- Owner Doctor còn phải cung cấp UUIDv4 và internal slot projection; owner Notification cần nhận event schema3 UUIDv4. Appointment không đổi về ID số để chạy theo các implementation cũ.
- Owner Gateway cần prefix routes phù hợp thư viện và nối Appointment v2/Patient v3. Có thể kiểm tra Appointment trực tiếp qua cổng công bố ở trên.
- Chưa có recipient provider thì outbox giữ PENDING_ENRICHMENT/retry; không tạo người nhận/token giả hoặc bỏ sự kiện. Đặt `APPOINTMENT_RECIPIENT_URL` tới provider thật khi owner cung cấp.
