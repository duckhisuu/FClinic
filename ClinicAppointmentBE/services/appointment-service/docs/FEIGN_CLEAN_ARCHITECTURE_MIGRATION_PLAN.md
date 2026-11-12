# Kế Hoạch Chuyển Đổi `appointment-service` Theo Cấu Trúc Clean Architecture (`job-service`) & `FeignClient` (`ats_be`)

> **Phạm vi & Nguyên tắc chốt:**
> 1. **Giữ nguyên cơ chế xác thực qua `auth-service`** (`AppointmentAuthentication` → `AuthIdentityVerifier` → `AuthAppointmentScopeAdapter` + `PatientServiceTokenProvider`), không chuyển sang Gateway header `X-User-*` như `ats_be` để đảm bảo tương thích với `api-gateway`, `auth-service` và toàn bộ contract/security tests hiện tại (`security-checks/`).
> 2. **Chuẩn hóa Clean Architecture** của `appointment-service` theo các điểm mạnh của `ats_be/services/job-service` (tách `PersistenceMapper`, giữ ranh giới tầng sạch).
> 3. **Thay thế tầng transport HTTP viết tay** (`ProviderHttp`, `AuthHttpTransport`, parse `JsonNode` thủ công) sang **Spring Cloud OpenFeign** theo cấu trúc `ats_be/services/application-service/client`, nhưng vẫn giữ nguyên các Outbound Port ở tầng `application` và toàn bộ ràng buộc bảo mật/contract (`X-FClinic-Reference-Contract`, TLS truststore, response bounds, distinct credentials, timeout/deadline).

---

## 1. Đối Chiếu Kiến Trúc Hiện Tại vs `ats_be`

### 1.1. Làm rõ vai trò tham chiếu trong `ats_be`
- **`ats_be/services/job-service`**: Đại diện cho **Clean Architecture / Hexagonal**:
  - `domain/` (`aggregate`, `model`, `repository`, `valueobject`)
  - `application/` (`command`, `exception`, `port/in`, `service`)
  - `infrastructure/` (`adapter`, `persistence/entity`, `persistence/mapper/JobPersistenceMapper`, `*JpaRepository`)
  - `api/` (`rest`, `dto`, `filter`) & `config/`
  - *Lưu ý:* `job-service` không có `FeignClient` vì chỉ nhận request.
- **`ats_be/services/application-service`**: Đại diện cho **`FeignClient` Pattern**:
  - Bật `@EnableFeignClients` + dependency `spring-cloud-starter-openfeign`.
  - Chia package `client/<service-name>/` gồm: `*Client.java`, `*FeignConfig.java` (không gắn `@Configuration`), `*ErrorDecoder.java`, `*FallbackFactory.java`.
  - Hỗ trợ dual-mode URL: `@FeignClient(name = "...", url = "${...:}")` — có URL thì gọi trực tiếp, rỗng thì resolve qua Eureka (`lb://service-name`).

### 1.2. Hiện trạng `appointment-service` (`ClinicAppointmentBE/services/appointment-service`)
| Tầng / Thành phần | Hiện trạng | Đánh giá & Hướng xử lý |
|---|---|---|
| **`domain/`** | `Appointment` aggregate, `Slot`, `BookingCode`, `BusinessId`, `AppointmentStatus`, `AppointmentError`, `domain/repository/AppointmentRepository`, `domain/event/*` | **Rất tốt, thuần Java**, không phụ thuộc Spring/JPA → **Giữ nguyên**. |
| **`application/`** | Đầy đủ `port/in/*UseCase`, `port/out/*Port`, `command/`, `result/`, `usecase/`, `security/AppointmentPolicy` | **Giữ nguyên các interface Port & UseCase** để logic nghiệp vụ không bị ảnh hưởng khi đổi transport sang Feign. |
| **`infrastructure/` (Persistence)** | `JpaAppointmentRepositoryAdapter` đang tự làm cả query JPA lẫn hàm `toDomain(JpaAppointmentEntity)` | **Tách `AppointmentPersistenceMapper`** sang `infrastructure/persistence/mapper/` giống `JobPersistenceMapper` của `job-service`. |
| **`infrastructure/` (Outbound HTTP)** | Dùng `java.net.http.HttpClient` viết tay trong `ProviderHttp` & `AuthHttpTransport`; parse `JsonNode` thủ công trong `HttpAppointmentReferenceAdapter`, `AuthIdentityVerifier`, `AuthAppointmentScopeAdapter`, `PatientServiceTokenProvider` | **Chuyển toàn bộ sang OpenFeign** (`infrastructure/client/<provider>/`), giữ lại các invariant kiểm tra contract & bảo mật. |
| **`api/` & `config/`** | `AppointmentController`, `AppointmentAuthentication` (`HandlerInterceptor`), `GlobalExceptionHandler`, `AppointmentSecuritySettings`, `ProviderCredentials` | **Giữ nguyên** hợp đồng API `/api/v2/appointments/**` và xác thực qua `auth-service`. |

---

## 2. Bản Đồ Chuyển Đổi Các Luồng Outbound HTTP Sang `FeignClient`

Hiện `appointment-service` có **5 nhóm kết nối HTTP ra ngoài**, cần thiết kế thành 5 FeignClient độc lập (mỗi client có `*FeignConfig` và credential riêng, tránh lẫn token):

| # | Service đích | Endpoint & Method | Auth hiện tại | Response Format & Contract Check | FeignClient mới |
|---|---|---|---|---|---|
| 1 | **`patient-service`** | `GET /api/v3/patients/{patientId}` | `Bearer <machine-token>` (từ `PatientServiceTokenProvider`, không forward token user) | `ApiResponse<PatientProfileDto>` (`status`, `message`, `data`) | `PatientClient` |
| 2 | **`doctor-service`** | `GET /internal/v1/doctors/{doctorId}`<br>`GET /internal/v1/doctors/{doctorId}/slots/{slotId}`<br>`GET /internal/v1/doctors/{doctorId}/slots?date={date}` | `Basic` (`doctorClientId` / `doctorClientSecret`) | `ApiResponse<...>` + header `X-FClinic-Reference-Contract: appointment-doctor-reference/1` | `DoctorClient` |
| 3 | **`auth-service` (Recipients)** | `POST /api/v1/auth/internal/appointment-recipient-accounts` | `Basic` (`recipientClientId` / `recipientClientSecret`) | `ApiResponse<RecipientAccountsDto>` + header `X-FClinic-Reference-Contract: appointment-recipient-accounts/1` | `AuthRecipientClient` |
| 4 | **`auth-service` (Scope)** | `POST /api/v1/auth/scopes` | `Basic` (`scopeClientId` / `scopeClientSecret`) | JSON phẳng (`ScopeDecisionDto`), echo chính xác `request` + `evaluatedAt` + `evidence` | `AuthScopeClient` |
| 5 | **`auth-service` (Introspect)** & **Keycloak (Token)** | `POST /api/v1/auth/introspect` (`form-urlencoded`)<br>`POST .../protocol/openid-connect/token` (`form-urlencoded`) | `Basic` (`clientId`/`clientSecret` cho introspect; `patientTokenClientId`/`Secret` cho Keycloak) | JSON phẳng (`IntrospectionResponseDto`, `KeycloakTokenDto`) | `AuthIntrospectClient` & `KeycloakTokenClient` |

> **Lưu ý đặc biệt về URL cấu hình trong test & production:**
> - `patient-url` và `doctor-url` là **base URL** (VD: `http://localhost:8081`, `http://localhost:8082`) → dùng chuẩn `@FeignClient(name = "patient-service", url = "${appointment.patient-url:}")` và hỗ trợ Eureka `lb://` khi để trống.
> - `appointment.security.introspection-uri`, `appointment.security.scope-uri`, `appointment.recipient-url`, `appointment.security.patient-token-uri` hiện có thể chứa **full URI kèm path** (trong `AppointmentSecurityTcpTest` bị override thành `http://127.0.0.1:<port>/introspect`, `/scope`, hoặc `ProviderBoundaryTest` là `/accounts`).
> - **Giải pháp tương thích 100%:** Với các client dùng full URI (`AuthIntrospectClient`, `AuthScopeClient`, `AuthRecipientClient`, `KeycloakTokenClient`), sử dụng **tham số `URI` đầu tiên của Spring Cloud OpenFeign** (VD: `@PostMapping ResponseEntity<...> introspect(URI endpoint, @RequestBody ...)` với `url = "http://localhost"` placeholder ở `@FeignClient`), hoặc giữ resolver chuyển `auth-url` (`lb://auth-service` khi không cấu hình full URI). Cách truyền `URI` đảm bảo **không phải sửa bất kỳ key `.env` / `application.yml` / `AppointmentSecuritySettings` nào**.

---

## 3. Cấu Trúc Thư Mục Mục Tiêu Trong `appointment-service`

```text
com.fclinic.appointmentservice
├── AppointmentServiceApplication.java          (+ @EnableFeignClients)
├── api/                                        (Giữ nguyên contract HTTP & security interceptor)
│   ├── AppointmentAuthentication.java
│   ├── AppointmentRequestIdFilter.java
│   ├── controller/
│   ├── dto/
│   ├── exception/GlobalExceptionHandler.java
│   └── mapper/
├── application/                                (KHÔNG import Feign / Spring Data / JPA)
│   ├── AppointmentActor.java
│   ├── AppointmentSchedulingPolicy.java
│   ├── command/
│   ├── exception/
│   ├── port/
│   │   ├── in/                                 (UseCase interfaces)
│   │   └── out/                                (Port interfaces - giữ nguyên)
│   ├── result/
│   ├── security/AppointmentPolicy.java
│   └── usecase/
├── config/
│   ├── AppointmentConfig.java
│   ├── AppointmentSecuritySettings.java
│   ├── AppointmentSettings.java
│   ├── AppointmentWebSecurityConfiguration.java
│   ├── ProviderCredentialPolicy.java
│   ├── ProviderCredentials.java
│   └── ... (RabbitMQ, RedisLock, Scheduling)
├── domain/                                     (Thuần Java)
│   ├── aggregate/Appointment.java
│   ├── event/
│   ├── model/AppointmentStatus.java
│   ├── repository/AppointmentRepository.java
│   ├── vo/
│   ├── AppointmentError.java
│   └── Slot.java
└── infrastructure/
    ├── client/                                 [MỚI - Pattern từ ats_be/application-service]
    │   ├── config/
    │   │   ├── FeignGlobalConfig.java          (Request.Options timeout, TLS Client từ ProviderTls, Loopback/HTTPS guard)
    │   │   ├── FeignHeaderGuards.java          (Helper BasicAuth header, Content-Type/Cache-Control, X-FClinic-Reference-Contract check)
    │   │   └── FeignJacksonSupport.java        (Shared strict validation / max response size guard)
    │   ├── patient/
    │   │   ├── PatientClient.java
    │   │   ├── PatientFeignConfig.java         (NO @Configuration: Bearer token interceptor + PatientErrorDecoder)
    │   │   ├── PatientErrorDecoder.java        (404 -> PATIENT_NOT_FOUND, 5xx -> unavailable)
    │   │   └── dto/PatientProfileDto.java
    │   ├── doctor/
    │   │   ├── DoctorClient.java
    │   │   ├── DoctorFeignConfig.java          (NO @Configuration: BasicAuth + Retryer cho GET + DoctorErrorDecoder)
    │   │   ├── DoctorErrorDecoder.java         (404 -> MissingReference / 404, 5xx -> unavailable)
    │   │   └── dto/
    │   │       ├── DoctorReferenceDto.java
    │   │       └── DoctorSlotDto.java
    │   ├── auth/
    │   │   ├── AuthRecipientClient.java
    │   │   ├── AuthRecipientFeignConfig.java   (NO @Configuration: BasicAuth recipient + ErrorDecoder)
    │   │   ├── AuthScopeClient.java
    │   │   ├── AuthScopeFeignConfig.java       (NO @Configuration: BasicAuth scope + ErrorDecoder)
    │   │   ├── AuthIntrospectClient.java
    │   │   ├── AuthIntrospectFeignConfig.java  (NO @Configuration: BasicAuth introspect + ErrorDecoder)
    │   │   └── dto/
    │   │       ├── RecipientLookupRequestDto.java
    │   │       ├── RecipientAccountsDto.java
    │   │       ├── ScopeQueryDto.java
    │   │       └── ScopeDecisionDto.java
    │   └── keycloak/
    │       ├── KeycloakTokenClient.java
    │       ├── KeycloakFeignConfig.java        (NO @Configuration: BasicAuth client_credentials + ErrorDecoder)
    │       └── dto/KeycloakTokenDto.java
    ├── persistence/
    │   ├── mapper/
    │   │   └── AppointmentPersistenceMapper.java [MỚI - Tách theo JobPersistenceMapper của job-service]
    │   ├── SpringDataJpaAppointmentRepository.java
    │   └── SpringDataJpaOutboxEventRepository.java
    ├── adapter/
    │   ├── FeignAppointmentReferenceAdapter.java (Thay HttpAppointmentReferenceAdapter, vẫn impl 3 Port)
    │   ├── JpaAppointmentRepositoryAdapter.java  (Inject AppointmentPersistenceMapper)
    │   └── ... (Outbox, Lock, Transaction, History adapters giữ nguyên)
    └── security/
        ├── AuthAppointmentScopeAdapter.java      (Inject AuthScopeClient thay AuthHttpTransport)
        ├── AuthIdentityVerifier.java             (Inject AuthIntrospectClient thay AuthHttpTransport)
        └── PatientServiceTokenProvider.java      (Inject KeycloakTokenClient thay AuthHttpTransport)
```

---

## 4. Các Ràng Buộc Kỹ Thuật Phải Bảo Toàn Khi Viết Feign

Để quá trình chuyển sang `FeignClient` **không làm giảm mức độ an toàn bảo mật** vốn có của `appointment-service`, các cơ chế sau phải được giữ nguyên trong tầng Feign:

1. **TLS Truststore & Chặn HTTP non-loopback**:
   - Hiện `ProviderHttp` và `AuthHttpTransport` dùng `ProviderTls` (nạp `PKCS12` truststore nếu cấu hình) và **chặn HTTP thường** nếu host không phải `localhost` / `127.0.0.1` / `[::1]` (chống SSRF / gửi credential qua plaintext).
   - **Cách giữ trên Feign:** Tạo custom `feign.Client` bean trong `FeignGlobalConfig` (bọc `feign.httpclient.ApacheHttp5Client` hoặc `feign.Client.Default` dùng `SSLContext` từ `ProviderTls` + `RequestInterceptor` kiểm tra scheme `https` hoặc loopback `http`, tắt redirect).
2. **Giới hạn kích thước response (256 KB) & phát hiện duplicate JSON keys**:
   - `LimitedBody` chặn response > `262144` bytes; `AuthHttpTransport` bật `StreamReadFeature.STRICT_DUPLICATE_DETECTION` và `DeserializationFeature.FAIL_ON_TRAILING_TOKENS`.
   - **Cách giữ trên Feign:** Cấu hình custom Feign `Decoder` bọc Jackson `JsonMapper` có `STRICT_DUPLICATE_DETECTION` và kiểm tra độ dài body `<= 262144` bytes (cũng xử lý lỗi `FeignException` / `DecodeException` thành `AppointmentError.unavailable()`).
3. **Deadline Budget (5s/request, tối đa 2s/call)**:
   - Mỗi client adapter giữ kiểm tra `deadline - System.nanoTime() <= 0 -> AppointmentError.unavailable()` trước khi gọi Feign.
   - Truyền dynamic timeout hoặc cấu hình `Request.Options` (`connectTimeout = 1000ms`, `readTimeout = 2000ms`).
4. **Kiểm tra header `X-FClinic-Reference-Contract` & Schema**:
   - `DoctorClient` & `AuthRecipientClient` trả `ResponseEntity<ApiResponse<...>>` để Adapter kiểm tra `response.getHeaders().get("X-FClinic-Reference-Contract")` khớp chính xác `["appointment-doctor-reference/1"]` / `["appointment-recipient-accounts/1"]`.
   - Kiểm tra `ApiResponse.status() == 200`, `message != null`, `data != null`, các invariant domain (`patientCode = "PAT-" + id`, regex phone, `consultationFee >= 0`, `active` boolean, `slotDate/startTime/endTime`).
5. **Tách biệt `ErrorDecoder` theo client**:
   - `404` ở `PatientClient` → `AppointmentError(404, "PATIENT_NOT_FOUND", ...)`
   - `404` ở `DoctorClient`:
     - Với `getDoctor` và `getSlots` → `AppointmentError(404, "DOCTOR_NOT_FOUND", ...)`
     - Với `getSlot` → `AppointmentError.conflict("SLOT_NOT_BOOKABLE", ...)` *(lưu ý: phân biệt theo `methodKey` trong `DoctorErrorDecoder`!)*
   - Mọi `401`/`403`/`5xx`/network timeout/decode error ở downstream → `AppointmentError.unavailable()` (HTTP `503`), **không** leak stacktrace hoặc token ra ngoài.
6. **Retry có chọn lọc**:
   - Chỉ gắn `Retryer` (ví dụ `new Retryer.Default(100, 300, 2)`) cho các request `GET` idempotent (`DoctorClient`, `PatientClient`) khi lỗi mạng/503 trong phạm vi deadline, **không** retry `POST` (`AuthScopeClient`, `AuthIntrospectClient`, `AuthRecipientClient`, `KeycloakTokenClient`) bằng `Retryer.NEVER_RETRY`.

---

## 5. Lộ Trình Triển Khai Từng Pha

### Pha 1 — Chuẩn bị OpenFeign & Persistence Mapper (Low-risk)
1. Thêm `spring-cloud-starter-openfeign` vào `services/appointment-service/pom.xml`.
2. Thêm `@EnableFeignClients(basePackages = "com.fclinic.appointmentservice.infrastructure.client")` vào `AppointmentServiceApplication.java`.
3. Tách `AppointmentPersistenceMapper.java` ra khỏi `JpaAppointmentRepositoryAdapter.java` để khớp cấu trúc `job-service` (`persistence/mapper/`).
4. Tạo `infrastructure/client/config/` (`FeignGlobalConfig.java`, custom `feign.Client` với `ProviderTls` + URI guard, custom strict `Decoder`).
5. Chạy `mvn test -pl services/appointment-service` để đảm bảo baseline vẫn xanh.

### Pha 2 — Chuyển `PatientClient`, `DoctorClient`, `AuthRecipientClient` sang Feign
1. Tạo DTOs, `*Client`, `*FeignConfig`, `*ErrorDecoder` cho `patient`, `doctor`, `auth` (recipient).
2. Triển khai `FeignAppointmentReferenceAdapter` (hoặc refactor trực tiếp `HttpAppointmentReferenceAdapter` giữ nguyên bean & constructor tương thích hoặc cập nhật test).
3. Cập nhật `application.yml` thêm cấu hình `spring.cloud.openfeign.client.config.default` (timeouts, loggerLevel).
4. Cập nhật `ProviderBoundaryTest.java` và chạy test kiểm chứng với mock `HttpServer` (giữ nguyên toàn bộ test cases: check `Bearer`, `Basic`, `X-FClinic-Reference-Contract`, `404`, hidden slot, non-loopback rejection).

### Pha 3 — Chuyển `AuthIntrospectClient`, `AuthScopeClient`, `KeycloakTokenClient` sang Feign
1. Tạo `AuthIntrospectClient`, `AuthScopeClient`, `KeycloakTokenClient` cùng `*FeignConfig` và DTOs.
2. Refactor `AuthIdentityVerifier`, `AuthAppointmentScopeAdapter`, `PatientServiceTokenProvider` dùng Feign client thay cho `AuthHttpTransport`.
3. Giữ nguyên tất cả rule verify: `contractVersion`, `iss`, `aud`, `exp`, `principalType == "USER"`, `request` echo exact match, `evaluatedAt` window, `validFrom`/`validUntil`, `access_token` cache & refresh window.
4. Cập nhật các unit tests:
   - `AuthIdentityVerifierTest.java`
   - `PatientServiceTokenProviderTest.java`
   - `AuthTransportTest.java` (chuyển sang test tầng Feign client/decoder: Basic Auth, 401→503, duplicate JSON key rejection, 256KB body limit, non-loopback HTTP rejection).
   - `AppointmentSecurityTcpTest.java` (chạy end-to-end với `EmbeddedPostgres` + mock `HttpServer`).

### Pha 4 — Cập nhật Cross-Service Contract Verification & Dọn dẹp
1. Cập nhật `ClinicAppointmentBE/security-checks/VerifyServiceContracts.java` để truyền/mock Feign client interface (hoặc Feign proxy) thay cho `AuthHttpTransport` trong các kiểm tra `AuthIdentityVerifier` & `AuthAppointmentScopeAdapter`.
2. Loại bỏ code HTTP thủ công dư thừa (`ProviderHttp.java`, `AuthHttpTransport.java` nếu không còn file nào tham chiếu — lưu ý kiểm tra xem `security-checks/` hoặc test khác có còn dùng `AuthHttpTransport.JSON` / `AuthHttpTransport.basic` hay không trước khi xóa hoặc thay bằng utility tương đương).
3. Chạy toàn bộ test suite:
   - `mvn test -pl services/appointment-service`
   - `mvn test` toàn monorepo `ClinicAppointmentBE`
   - `ClinicAppointmentBE/security-checks/Run-Contracts.ps1`

---

## 6. Checklist Tiêu Chí Hoàn Thành (Definition of Done)
- [x] `appointment-service` compile & pass 100% tests trong `services/appointment-service/src/test`.
- [x] `security-checks/Run-Contracts.ps1` chạy PASS (cả `VerifyServiceContracts` và `VerifyProviderContracts`).
- [x] Không còn `java.net.http.HttpClient` viết tay cho business/security calls trong `appointment-service` (trừ trong test harness).
- [x] Toàn bộ outbound HTTP calls đi qua `@FeignClient` trong `infrastructure/client/`, tách `*FeignConfig` & `*ErrorDecoder` riêng cho từng service theo đúng pattern `ats_be/application-service`.
- [x] Persistence mapping tách riêng vào `infrastructure/persistence/mapper/AppointmentPersistenceMapper.java` giống `job-service`.
- [x] Không phá vỡ bất kỳ endpoint, DTO response, hay security contract nào giữa `appointment-service` ↔ `auth-service` / `patient-service` / `doctor-service`.

---

## 7. Kết Quả Triển Khai — 12/11/2026

- Đã bật OpenFeign và LoadBalancer, triển khai sáu client riêng: Patient, Doctor, Auth Recipient, Auth Scope, Auth Introspect và Keycloak Token. Mỗi client có config/error decoder riêng; các config chỉ thuộc Feign child context.
- `FeignAppointmentReferenceAdapter` thay adapter HTTP cũ, giữ nguyên ba output port. Identity, scope và machine-token provider nhận client interface và xử lý DTO; kiểm tra JSON/field bắt buộc/kiểu scalar nằm trong `FeignJacksonSupport`.
- `GuardedFeignClient` bọc `feign.Client.Default`: dùng socket factory từ `ProviderTls`, xác minh certificate/hostname, cấm redirect và HTTP ngoài loopback. Giới hạn 32 exchange đồng thời mỗi client, body tối đa 262144 byte và deadline bao gồm cả đọc body. `DeadlineOptions` truyền deadline hiện hành; mỗi attempt tối đa 2 giây, connect tối đa 1 giây.
- Patient/Doctor GET retry tối đa hai attempt khi lỗi I/O/timeout/503, cách nhau 100 ms và chỉ khi còn budget. Các POST dùng `Retryer.NEVER_RETRY`. Header contract được kiểm tra trước cả error decoder, bao gồm response 404.
- Patient/Doctor URL rỗng đi qua discovery/load balancing; guard kiểm tra URI instance cuối cùng trước khi gửi credential. Các client dùng full URI tiếp tục nhận tham số `URI`, giữ nguyên path cấu hình và tên biến môi trường.
- JPA adapter inject `AppointmentPersistenceMapper`; mapper xử lý cập nhật entity và chuyển entity thành aggregate. Các query/optimistic version checks vẫn ở repository adapter.
- Đã xóa `ProviderHttp`, `AuthHttpTransport` và `HttpAppointmentReferenceAdapter`. Source production Appointment không còn `java.net.http`, parse `JsonNode` trong các reference/security adapter, hoặc dependency Feign/JPA trong application/domain.
- Đã cập nhật cả hai cross-service verifiers và giữ Java regression tests của Appointment trong Git qua ngoại lệ `.gitignore`.

### Kiểm chứng

Chạy bằng JDK 21 và Maven 3.9.16. Log cục bộ nằm tại `.runtime/` của workspace.

| Kiểm tra | Kết quả |
| --- | --- |
| Baseline trước migration | 34 test Appointment pass |
| `mvn clean test -pl services/appointment-service -am` | 49 test, 0 failure/error/skip; build sạch không dùng class transport cũ |
| `security-checks/Run-Contracts.ps1` | PASS cả `VerifyServiceContracts` (8 checks) và `VerifyProviderContracts` (5 checks) |
| `mvn test` toàn backend | PASS toàn bộ 10 module; 331 test, 0 failure/error/skip |

Coverage bổ sung gồm Basic/Bearer và full URI, 401/403/503, phân biệt doctor/slot 404, duplicate contract header, hidden/duplicate slots, duplicate JSON keys/trailing tokens/sai kiểu scalar, body quá lớn cả chunked, redirect, URI không an toàn, budget hết và body chậm. TLS test chạy HTTPS với PKCS12 riêng, xác nhận CA không tin cậy và hostname sai bị từ chối. Discovery test dùng Feign Spring proxy với base URL rỗng và kiểm tra guard trên URI đã resolve. Scope tests kiểm tra exact echo (kể cả field null bắt buộc), cửa sổ evaluatedAt, assignment còn hiệu lực và patient IDs trùng.