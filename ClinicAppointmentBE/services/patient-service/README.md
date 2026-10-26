# Patient Service v2

Patient Service sở hữu hồ sơ hành chính UUID, medical summaries và hai loại revision. Module này là target binary v2; không tương thích với Patient database v1 hoặc consumer còn Long patientId.

## Phạm vi hiện có

- 8 operation theo Patient canonical OpenAPI trong pack được chỉ định.
- Create-only/shared phone, UUID v4 và PAT-cùng UUID, full PUT/version/no-op.
- Medical linked/walk-in, nested lookup, author/current-scope checks, local locking và optimistic concurrency.
- Entity + revision atomic; lỗi ghi revision rollback toàn bộ; lookup ngoài write transaction.
- API error riêng Patient v2, không sửa common-web hoặc module khác.
- Không còn route/writer/seed Patient v1 trong target binary. Chưa deploy hoặc thực hiện cutover.

Tình trạng test và gate ở [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md). Cả4ports identity/scope/Doctor/Appointment có production HTTPS adapters opt-in, contracts PROJECT_DECIDED; Doctor B-04 và Appointment B-05 đã verify với actual isolated provider binaries; real Auth/scope và B-02 chưa verify, defaults vẫn fail closed. Xem [M5-D2](docs/integration-readiness/14_M5_D2_APPOINTMENT_PROVIDER_VERIFICATION.md) để phân biệt real B-05 với B-02 TCP dùng doubles. Xem [M5-A identity](docs/integration-readiness/07_M5_A_IDENTITY_IMPLEMENTATION.md), [M5-B scope](docs/integration-readiness/08_M5_B_SCOPE_IMPLEMENTATION.md), [M5-C reference](docs/integration-readiness/09_M5_C_REFERENCE_IMPLEMENTATION.md).

## Build và kiểm thử

Từ repository root với Java 21 và Maven:

~~~powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am test
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify
~~~

- Surefire: unit/policy/input/orchestration/authentication tests.
- Failsafe khi verify: PatientServiceIT (MockMvc/JPA/PG), PatientIdentityTcpIT/PatientScopeTcpIT/PatientReferenceTcpIT (actual random-port servlet TCP + production adapters + PG). Upstream Auth/scope/reference providers là test doubles, không real integration.
- IT tạo cluster mới trong target/patient-v2-it-*, bind 127.0.0.1, cổng ngẫu nhiên, áp approved empty-schema SQL. Không dùng DB_HOST hoặc volume/database sẵn có. Cluster đóng và thư mục data tạm được dọn sau test.
- Docker không bắt buộc cho test này: dùng [Zonky Embedded PostgreSQL](https://github.com/zonkyio/embedded-postgres) với native binaries, dependency test scope. Đây là PostgreSQL process thật, không phải H2.
- Test dùng Clock và dữ liệu synthetic từ fixture registry của pack. Identity, work scope và Doctor/Appointment trong IT là test doubles; không chứng minh các provider thật đã hoạt động.
- Canonical OAS được đọc từ pack vào test resources. M5-D dùng test-only networknt3.0.8 Draft2020-12 + format assertions trên response samples của8operations; không là exhaustive OpenAPI HTTP/security conformance.
- Reports: target/surefire-reports và target/failsafe-reports. Không coi test count là coverage% hoặc bằng chứng hoàn tất toàn bộ TC-01..36.

## Chuẩn bị database local v2

Nguồn SQL: src/main/resources/db/schema/V001__patient_v2_empty.sql. File giữ nguyên approved DDL, có checksum ghi trong evidence; **không phải upgrade v1** và không được ứng dụng tự chạy khi startup. Flyway chưa được adopt theo D-15 nên chưa cài migration runner.

Chỉ trên database rỗng do bạn tạo riêng, ví dụ patient_v2_local:

~~~powershell
psql -h localhost -U <local-owner> -d patient_v2_local -v ON_ERROR_STOP=1 -f ClinicAppointmentBE/services/patient-service/src/main/resources/db/schema/V001__patient_v2_empty.sql
~~~

Dùng user app có quyền cần thiết cho tables/sequences của database đó. Không trỏ target binary vào patient_db cũ. Không có seed tự động; fixture tests không phải production seed.

## Chạy target binary

Phải chọn rõ datasource v2 đã chuẩn bị và cung cấp credentials qua environment:

| Biến | Ý nghĩa |
|---|---|
| PATIENT_V2_DB_URL | JDBC URL database v2 riêng, không có fallback v1 |
| PATIENT_V2_DB_USERNAME | Database app user |
| PATIENT_V2_DB_PASSWORD | Database password qua secret/environment |
| EUREKA_ENABLED | false mặc định; chỉ bật sau phối hợp Gateway/discovery |
| EUREKA_SERVER_URL | Discovery URL khi bật, default localhost:8761 |

Sau khi cấu hình environment:

~~~powershell
java -jar ClinicAppointmentBE/services/patient-service/target/patient-service-1.0.0-SNAPSHOT.jar
~~~

Port 8081, ddl-auto:validate, SQL auto-init tắt. /actuator/health và /actuator/info được expose; health không hiển thị detail.

IdentityVerifierPort mặc định disabled/fallback: thiếu/malformed bearer trả401; syntax-valid bearer trả503. Bật profile identity-introspection cần PATIENT_AUTH_INTROSPECTION_URI/ISSUER/CLIENT_ID/CLIENT_SECRET, audience đúng provider, TLS trust và optional protected machine-subject registry. [Hướng dẫn M5-A](docs/integration-readiness/07_M5_A_IDENTITY_IMPLEMENTATION.md#5-configuration-secrets-transport) mô tả contract mới và cấu hình đầy đủ. Không có dev token/fake role header hoặc permissive fallback; chỉ test source có doubles.

## Hợp đồng và các adapter còn thiếu

- IdentityVerifierPort: đã implement HTTPS introspection v1 PROJECT_DECIDED, strict issuer/audience/expiry/current activation/mapping và server registry serviceName. Provider thật/config/provisioning chưa verify; không tương thích ngầm draft validateCaller. TrustedCaller là kiểu nội bộ, không định nghĩa JWT claims.
- WorkScopePort: M5-B production adapter tách administrative/clinical authority, strict operation/caller/resource/visit binding và current evidence; provider thật chưa verify. SearchScope null→403, authorized empty set→200/count0; technical outage→typed503, không deny giả. [Cấu hình đầy đủ](docs/integration-readiness/08_M5_B_SCOPE_IMPLEMENTATION.md#5-configuration-và-rollout): profile scope-authority cần PATIENT_ADMIN_SCOPE_URI/CLIENT_ID/CLIENT_SECRET và PATIENT_CLINICAL_SCOPE_URI/CLIENT_ID/CLIENT_SECRET, credentials riêng introspection, TLS trust. Default disabled deny403; enabled settings lỗi fail startup.
- DoctorClientPort: production protected full-Doctor consumer profile patient-doctor-reference/1, selected versioned404→empty→422, inactive422; malformed/technical503. Actual Doctor protected provider đã verified trên isolated runtime ở [M5-D1](docs/integration-readiness/13_M5_D1_DOCTOR_PROVIDER_VERIFICATION.md); không whole-chain verification.
- AppointmentClientPort: production minimum UUID SERVICE projection patient-appointment-medical-reference/1, strict4-status enum trước internal cancelled boolean; mismatch/missing/cancelled422, unknown/technical503. Không public full/Long fallback hoặc remote mutations. [M5-C configuration](docs/integration-readiness/09_M5_C_REFERENCE_IMPLEMENTATION.md#4-production-code-và-configuration): separate doctor-reference/appointment-reference profiles, PATIENT_DOCTOR_REFERENCE_URI/CLIENT_ID/CLIENT_SECRET và PATIENT_APPOINTMENT_REFERENCE_URI/CLIENT_ID/CLIENT_SECRET, CA trust; credentials không reuse identity/scope/other reference.
- M5-A contract local được PROJECT_DECIDED theo ủy quyền người dùng; muốn chạy provider thật cần provider compatible và provision config/authority tương ứng, không tự nhận shared adoption. Những boundary khác còn theo handover. Không lấy body doctorId, phone/email hoặc browser headers làm trusted identity.

Chi tiết bàn giao ở [INTEGRATION_HANDOVER.md](INTEGRATION_HANDOVER.md).

## Concurrency, audit và vận hành

Profile PUT và medical create lock cùng row Patient, serialize deactivate/create. Profile PUT còn kiểm expected version; no-op đăng ký optimistic check ở commit. Medical PUT optimistic, bắt buộc author subject + doctorId + current scope; no-op vẫn yêu cầu reason và kiểm version lúc commit. Audit snapshot ghi sau flush, cùng write transaction.

Chỉ duplicate constraint medical_records_appointment_id_key được map APPOINTMENT_RECORD_EXISTS. Không biến mọi integrity failure thành business409. Các lỗi còn lại generic500; không gửi exception/SQL/clinical data về client. Routine logger không ghi body/full phone/clinical/token/audit snapshot. Database operator cần áp chính sách logging/redaction phù hợp, độc lập application logger.

D-21: canonical OAS chưa liệt kê503 cho mọi operation dù shared error policy yêu cầu503 khi Auth unavailable. Implementation giữ fail-closed503 và ghi discrepancy, chưa tự sửa approved OAS.

POST chưa idempotent. Sau response không chắc chắn, đối soát bằng GET/search theo quyền; linked medical có thể filter appointmentId. Không blind retry POST/PUT.

## Migration và cutover

Chưa có inventory database đang dùng, coordinated window, clone/restore evidence hoặc owner adoption D-15. Vì vậy chưa thực hiện Long→UUID upgrade hoặc mở lại ghi trên môi trường chung.

Trình tự bắt buộc: inventory → backup/restore clone → stable persisted mapping Long↔UUID → binaries/consumers UUID sẵn → maintenance/drain mọi v1 writer/job/seed/direct port → mỗi owner migrate database mình → validate constraints/counts/revisions/mapping → target-only startup/retire v1 → smoke/signoff → mở ghi.

Sau shared phone/UUID, rollback bằng coordinated restore hoặc forward fix đã duyệt; không chạy v1 binary trên target schema. Không merge/xóa người theo phone.


## M5-D real provider verification

[Report](docs/integration-readiness/10_M5_D_PROVIDER_CONTRACT_VERIFICATION.md), [provider handoff](docs/integration-readiness/11_PROVIDER_COMPATIBILITY_HANDOFF.md), [runbook và configuration](docs/integration-readiness/12_REAL_INTEGRATION_RUNBOOK.md). Harness executable read-only opt-in bằng profile real-provider-contracts; default verify không chạy external providers. Current254regressionPASS, provider cases BLOCKED/NOT EXECUTED vì chưa có compatible binary/config. Không đổi production ports/decoder hoặc tạo provider double mới.

## M5-D1 Doctor provider

[Actual B-04 report](docs/integration-readiness/13_M5_D1_DOCTOR_PROVIDER_VERIFICATION.md):5mandatory +1technical vector REAL PROVIDER PASS against Doctor executable JAR.59Doctor tests/254Patient normal tests/255Patient opt-in tests pass,F/E/S0. Real profile dùng một Failsafe execution giữ mọi defaultIT include và thêm harness; reports/summary mới ở target/real-provider-reports/current, observations vẫn target/real-provider-reports/observations.json. Historical preflight summary giữ nguyên. Doctor reference-only profile intentionally denies public directory/slot/mutation APIs; không thay Gateway/FE. B-05/B-02/Auth/scope vẫn chưa verified.
