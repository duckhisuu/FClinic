# FClinic Auth Service (Keycloak OIDC + UUIDv4 + Clean Architecture)

Service xác thực, phân quyền và quản lý tài khoản trung tâm của hệ thống **FClinic Microservices**, xây dựng theo mô hình **Clean Architecture** (`api`, `application`, `domain`, `infrastructure`) và tích hợp **Keycloak Identity Provider** chuẩn OAuth2/OIDC.

## 1. Kiến trúc & Nguyên tắc thiết kế

- **Keycloak là Identity Provider (IdP) trung tâm**:
  - Lưu trữ và băm mật khẩu an toàn, bảo vệ brute-force, phát hành JWT chữ ký bất đối xứng RS256, xoay khóa công khai qua JWKS (`/realms/fclinic/protocol/openid-connect/certs`), cấp phát Refresh Token, hỗ trợ Authorization Code + PKCE, Service-to-Service (`client_credentials`), Google Identity Broker và thu hồi phiên đăng nhập tức thì (`/users/{id}/logout`).
- **`auth-service` (`:8085`) là Facade & FClinic Domain Registry (`auth_db`)**:
  - Toàn bộ khóa chính và khóa định danh liên service (`users.id`, `roles.id`, `users.keycloak_id`, `users.patient_id`, `users.doctor_id`, `password_reset_otps.id`) đều sử dụng **UUIDv4 chuẩn hóa chữ thường**.
  - Lưu metadata nghiệp vụ phòng khám (`full_name`, `phone_number`, `patient_id`, `doctor_id`, trạng thái `active`, OTP đặt lại mật khẩu) với cơ chế **Saga bù trừ** (nếu lưu DB thất bại sau khi tạo user trên Keycloak, hệ thống tự động gọi `DELETE` user trên Keycloak để tránh tài khoản mồ côi).
  - Tự đóng vai trò **OAuth2 Resource Server** xác thực Bearer JWT qua JWKS của Keycloak và đối chiếu trạng thái `active` thời gian thực trong `auth_db`.
  - Cung cấp endpoint introspection liên service chuẩn **`patient-identity-introspection/2`** tại `POST /api/v1/auth/introspect` cho `patient-service` và các dịch vụ nội bộ.

## 2. Cấu trúc Package

```
com.fclinic.authservice
├── api
│   ├── controller        # AuthController, AdminUserController, StaffController, IntrospectionController
│   ├── dto               # Request / Response records (UUIDv4, redacted toString)
│   ├── exception         # GlobalExceptionHandler -> ApiResponse
│   └── mapper            # AuthApiMapper
├── application
│   ├── command           # Input commands cho use cases
│   ├── model             # Application results & policy objects
│   ├── port/in           # RegisterUseCase, LoginUseCase, IntrospectTokenUseCase, ManageUserUseCase, ...
│   ├── port/out          # KeycloakIdentityPort, UserRepositoryPort, RoleRepositoryPort, ...
│   └── service           # AuthApplicationService, AdminUserService, StaffPatientAccountService, TokenVerificationService, PasswordRecoveryService
├── domain
│   ├── aggregate         # User (UUIDv4 invariants)
│   ├── exception         # Typed domain AuthException hierarchy
│   ├── model             # PasswordResetOtp
│   ├── util              # UuidV4 validator/generator
│   └── valueobject       # Role, RoleName (ROLE_ADMIN, ROLE_DOCTOR, ROLE_PATIENT, ROLE_STAFF)
└── infrastructure
    ├── adapter           # KeycloakRestAdapter, Jpa*RepositoryAdapter, OtpDeliveryAdapter
    ├── config            # SecurityConfig, KeycloakJwtAuthenticationConverter, *Properties
    ├── entity            # JpaUserEntity, JpaRoleEntity, JpaPasswordResetOtpEntity (extends BaseEntity)
    ├── mapper            # AuthPersistenceMapper
    └── persistence       # Spring Data JPA repositories
```

## 3. Tài khoản Seed mặc định (Realm `fclinic` & Flyway `V2`)

| Role | Username | Password | User UUID | Linked Business UUID |
|---|---|---|---|---|
| `ROLE_ADMIN` | `admin` | `Admin@123` | `11111111-1111-4111-8111-111111111101` | — |
| `ROLE_STAFF` | `staff_linh` | `Staff@123` | `11111111-1111-4111-8111-111111111102` | — |
| `ROLE_DOCTOR` | `bs_an` | `Doctor@123` | `11111111-1111-4111-8111-111111111103` | `doctorId = 33333333-3333-4333-8333-333333333301` |
| `ROLE_PATIENT` | `patient_hung` | `Patient@123` | `11111111-1111-4111-8111-111111111104` | `patientId = 74fba630-5953-44b8-86d6-bc236ac1213b` |

## 4. Chạy hạ tầng & Demo thủ công

1. Khởi động hạ tầng Docker (bao gồm `postgres-auth`, `postgres-keycloak`, `keycloak` tự động import [fclinic-realm.json](../../infra/keycloak/fclinic-realm.json)):
   ```bash
   cd ClinicAppointmentBE
   docker compose up -d postgres-auth postgres-keycloak keycloak mailhog
   ```
2. Khởi động `discovery-server` (`:8761`), `api-gateway` (`:8080`) và `auth-service` (`:8085`):
   ```bash
   mvn -f ClinicAppointmentBE/pom.xml -pl infra/discovery-server spring-boot:run
   mvn -f ClinicAppointmentBE/pom.xml -pl infra/api-gateway spring-boot:run
   mvn -f ClinicAppointmentBE/pom.xml -pl services/auth-service spring-boot:run
   ```
3. Kiểm thử các endpoint trực tiếp qua API Gateway (`http://localhost:8080/api/v1/auth/...`) hoặc Auth Service (`http://localhost:8085/api/v1/auth/...`) bằng `curl`, PowerShell `Invoke-RestMethod`, hoặc Postman.

