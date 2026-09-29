# Clinic Appointment System - FClinic

Hệ thống đặt lịch khám bệnh thông minh theo kiến trúc **Microservices Monorepo** với **Spring Cloud (Backend)** kết hợp **React Vite TypeScript (Frontend SPA)**.

---

## 1. Cấu Trúc Tổng Thể Dự Án

Dự án được phân tách thành 2 thư mục độc lập:

```
MSS301_FA26_GroupProject/
├── .github/workflows/
│   └── ci.yml                        # GitHub Actions CI workflow cho BE & FE
├── ClinicAppointmentBE/              # Backend Microservices Monorepo (Clean Architecture)
│   ├── common/
│   │   ├── common-events/            # DomainEvent base contracts & Integration events
│   │   └── common-web/               # BaseEntity, ApiResponse<T>, ErrorCode
│   ├── infra/
│   │   ├── discovery-server/         # Netflix Eureka Server (Port 8761)
│   │   └── api-gateway/              # Spring Cloud Gateway WebFlux (Port 8080)
│   ├── services/
│   │   ├── patient-service/          # Clean Architecture: API, Application, Domain, Infra
│   │   ├── doctor-service/           # Clean Architecture: API, Application, Domain, Infra
│   │   ├── appointment-service/      # Clean Architecture + Transactional Outbox + Redis Lock
│   │   └── notification-service/     # Clean Architecture + RabbitMQ EDA Consumer
│   ├── docker-compose.yml            # Postgres (3 DBs), Redis, RabbitMQ
│   ├── .env.example                  # File cấu hình mẫu
│   ├── .env                          # Biến môi trường chạy cục bộ
│   └── pom.xml                       # Maven Parent POM (Java 21, Spring Boot, Spring Cloud)
├── ClinicAppointmentFE/              # Frontend Client SPA (Pure React.js - NO TSX)
│   ├── src/
│   │   ├── services/                 # api.js client (base: http://localhost:8080 + mock fallback)
│   │   ├── components/               # Navbar, Sidebar, Layout, NotificationDrawer, Toast
│   │   ├── pages/
│   │   │   ├── DoctorSchedulePage.jsx # Danh sách bác sĩ & lưới chọn khung giờ khám
│   │   │   ├── BookingPage.jsx        # Wizard đặt lịch & Redis Lock + Outbox confirmation
│   │   │   ├── PatientHistoryPage.jsx # Tra cứu lịch hẹn theo SĐT/mã hẹn & hủy lịch
│   │   │   └── OutboxMonitorPage.jsx  # Monitor Transactional Outbox & Concurrent Lock test
│   │   ├── App.jsx
│   │   ├── main.jsx
│   │   └── index.css
│   ├── package.json                  # React 18, Vite, Tailwind CSS, Lucide
│   ├── vite.config.js
│   └── tailwind.config.js
├── Social-Media-Blog-Platform/       # Reference Clean Architecture monorepo
├── .gitignore
└── README.md
```

---

## 2. Bảng Cổng (Port Directory)

| Thành phần | Công nghệ | Cổng (Port) | Mô tả |
| :--- | :--- | :--- | :--- |
| **Eureka Discovery Server** | Netflix Eureka Server | `8761` | Service Registry & Discovery |
| **API Gateway** | Spring Cloud Gateway WebFlux | `8080` | Định tuyến trung tâm, CORS |
| **Patient Service** | Spring Boot + JPA | `8081` | Quản lý bệnh nhân |
| **Doctor Service** | Spring Boot + JPA | `8082` | Quản lý bác sĩ & khung giờ |
| **Appointment Service** | Spring Boot + Redisson | `8083` | Đặt lịch khám & Khóa phân tán |
| **Notification Service** | Spring Boot + RabbitMQ | `8084` | Lắng nghe & gửi thông báo |
| **PostgreSQL (Patient)** | PostgreSQL 16 | `5433` | Database `patient_db` |
| **PostgreSQL (Doctor)** | PostgreSQL 16 | `5434` | Database `doctor_db` |
| **PostgreSQL (Appointment)** | PostgreSQL 16 | `5435` | Database `appointment_db` |
| **Redis** | Redis 7 Alpine | `6379` | Distributed Lock chống double-booking |
| **RabbitMQ AMQP / UI** | RabbitMQ 3 Management | `5672` / `15672` | Hàng đợi sự kiện & giao diện quản trị |
| **Frontend Client SPA** | React + Vite + Tailwind | `5173` | Giao diện người dùng |

---

## 3. Hướng Dẫn Khởi Chạy 3 Bước Nhanh Chóng

### Bước 1: Khởi động Hạ tầng Containers (PostgreSQL, Redis, RabbitMQ)
Di chuyển vào thư mục `ClinicAppointmentBE`:
```bash
cd ClinicAppointmentBE
docker compose up -d
```
> [!NOTE]
> File `.env` đã được khởi tạo sẵn với các thông số mặc định (User: `fclinic_admin`, Pass: `fclinic_secret`). Bạn có thể kiểm tra RabbitMQ UI tại `http://localhost:15672` (User: `fclinic_rabbit` / Pass: `rabbit_secret`).

### Bước 2: Build & Khởi động Backend Microservices
Tại thư mục `ClinicAppointmentBE`:
```bash
# 1. Biên dịch toàn bộ modules
mvn clean compile -DskipTests

# 2. Khởi động theo thứ tự:
# Khởi động Eureka Server trước (Port 8761):
cd infra/discovery-server && mvn spring-boot:run

# Mở terminal mới, khởi động API Gateway (Port 8080):
cd infra/api-gateway && mvn spring-boot:run

# Mở các terminal tiếp theo cho các services:
cd services/patient-service && mvn spring-boot:run
cd services/doctor-service && mvn spring-boot:run
cd services/appointment-service && mvn spring-boot:run
cd services/notification-service && mvn spring-boot:run
```

### Bước 3: Khởi động Frontend Client SPA
Mở một terminal mới tại thư mục `ClinicAppointmentFE`:
```bash
cd ClinicAppointmentFE
npm install
npm run dev
```
Mở trình duyệt truy cập: **`http://localhost:5173`**

---

## 4. Kiểm Tra Thông Tuyến & REST Endpoints Qua API Gateway

Tất cả các cuộc gọi từ Frontend hoặc công cụ thử nghiệm (Postman / cURL) đều đi qua **API Gateway (Port 8080)**:

### 1. Bác sĩ & Khung giờ khám (Doctor Service):
```bash
curl -X GET http://localhost:8080/api/v1/doctors
curl -X GET http://localhost:8080/api/v1/doctors/1/slots
```

### 2. Đặt lịch khám (Appointment Service - có Distributed Lock):
```bash
curl -X POST http://localhost:8080/api/v1/appointments \
  -H "Content-Type: application/json" \
  -d '{
    "patientId": 1,
    "patientName": "Nguyễn Văn Hùng",
    "patientPhone": "0912345678",
    "doctorId": 1,
    "doctorName": "BS. CKII Nguyễn Văn An",
    "slotId": 1,
    "appointmentDate": "2026-09-23",
    "startTime": "08:00:00",
    "endTime": "08:30:00",
    "reasonForVisit": "Khám tim mạch định kỳ",
    "consultationFee": 350000.0
  }'
```

### 3. Tra cứu lịch hẹn:
```bash
curl -X GET "http://localhost:8080/api/v1/appointments/by-phone?phone=0912345678"
```

### 4. Quản lý bệnh nhân (Patient Service):
```bash
curl -X GET http://localhost:8080/api/v1/patients
```

### 5. Thông báo (Notification Service):
```bash
curl -X GET http://localhost:8080/api/v1/notifications
```

---

## 5. Điểm Nhấn Kiến Trúc Kỹ Thuật

1. **Distributed Locking (Redisson)**: `appointment-service` sử dụng `RLock lock = redissonClient.getLock("lock:slot:{doctorId}:{slotId}:{date}")` với `tryLock(waitTime=5s, leaseTime=10s)` để loại bỏ hoàn toàn khả năng 2 bệnh nhân đặt cùng 1 khung giờ.
2. **Database-per-service**: Ba cụm cơ sở dữ liệu PostgreSQL độc lập đảm bảo tính phân tán và phân chia trách nhiệm (bounded context).
3. **Event Notification (RabbitMQ)**: Giao tiếp bất đồng bộ qua queue `fclinic.appointment.notifications` giúp mở rộng hệ thống mà không làm nghẽn tiến trình đặt lịch chính.
4. **Reactive Gateway**: Sử dụng Spring Cloud Gateway WebFlux hoàn toàn phi phong tỏa (non-blocking) trên nền tảng Netty.
