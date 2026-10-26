# M5-D — Provider compatibility handoff v1

Ngày 24/10/2026. Đây là **implementation specification phía Patient / PROJECT_DECIDED**, chưa được thực hiện ở provider. Không tự đổi approved pack hoặc trạng thái adoption. [Kết quả](10_M5_D_PROVIDER_CONTRACT_VERIFICATION.md), [runbook](12_REAL_INTEGRATION_RUNBOOK.md).

## 1. Source anchors đã kiểm tra

Các đường dẫn dưới đây có line theo source snapshot được hash trong evidence M5-D; line không là bằng chứng runtime.

| Ref | File và vị trí | Quan sát |
|---|---|---|
| DOC-C | [DoctorController](../../../doctor-service/src/main/java/com/fclinic/doctorservice/api/controller/DoctorController.java):25,45–49 | GET /api/v1/doctors/{id}, Long; optional missing→exception, wrapped success |
| DOC-D | [DoctorResponse](../../../doctor-service/src/main/java/com/fclinic/doctorservice/api/dto/DoctorResponse.java):5–16 | Đủ11field, active primitive boolean; primitive không chứng minh payload luôn đúng contract |
| DOC-E | [Doctor error handler](../../../doctor-service/src/main/java/com/fclinic/doctorservice/api/exception/GlobalExceptionHandler.java):20–24 | Missing404 qua common ApiResponse.failure, thiếu code/fieldErrors/version marker |
| DOC-A | [Doctor application](../../../doctor-service/src/main/java/com/fclinic/doctorservice/application/usecase/DoctorApplicationService.java):43–45,112 | Detail không filter inactive; có auto-seed @PostConstruct hiện tại |
| APT-C | [AppointmentController](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/api/controller/AppointmentController.java):24,41–45,55–64,69–88 | Public v1/full DTO, Long patient filter/by-phone, booking từ body snapshots |
| APT-D | [AppointmentResponse](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/api/dto/AppointmentResponse.java):10–24 | Long patientId; patient name/phone/reason và scheduling fields, không minimum projection |
| APT-P | [BookAppointmentRequest](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/api/dto/BookAppointmentRequest.java):17–30 | Long patientId; name/phone client supplied |
| APT-J | [JpaAppointmentEntity](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/infrastructure/entity/JpaAppointmentEntity.java):29 | Persisted patientId Long; DB riêng Appointment |
| APT-S | [AppointmentStatus](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/domain/model/AppointmentStatus.java):3–8 | PENDING/CONFIRMED/COMPLETED/CANCELLED, thiếu NO_SHOW |
| APT-A | [AppointmentApplicationService](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/application/usecase/AppointmentApplicationService.java):39–58,61–108,169–205 | Chỉ repository/eventPublisher/lock dependencies, lock trước executeBooking; save+publish, seed. Không Patient validation/original-human authorization trong flow này |
| WEB | [Common ApiResponse](../../../../common/common-web/src/main/java/com/fclinic/common/web/ApiResponse.java):1–36 | Wrapper chung3fields; không sửa common-web trong task này |

Cả hai application.yml hiện HTTP port8082/8083, ddl-auto:update; scan src/main không tìm thấy machine authorization/TLS/version marker. Đây là kết luận về repo, không khẳng định mọi remote deployment đều có cùng config. Không chạy các binary legacy với mặc định đó.

## 2. Task DOC-REF-v1 — Doctor owner

**B-04, PAT-REF-DOC-001**. Implement trên isolated provider branch/task riêng:

1. Giữ route GET /api/v1/doctors/{id}, full shape DOC-D. Dùng [pinned schema](contracts/patient-doctor-reference-v1.schema.json) SHA098d3651fe4bc5daad3ec813ce459acfc624a0888ad209f789aa6a4bcd08c172. Không sparse DTO id/active ở profile này.
2. HTTPS TLS1.2/1.3 có chain/hostname validation. Cấu hình DB riêng/validate/seed explicit; không start với legacy URL/auto-seed để “verify read-only”.
3. Provision dedicated machine client Patient→Doctor; authenticate Basic theo URL-form encode từng clientId/clientSecret trước Base64 UTF8. Map credential phía server vào least privilege reference detail-read; không cấp list/create/slot-mutation. Không forward human bearer, không reuse identity/scope/Appointment secret. Direct port phải enforce; Gateway không thay thế.
4. Chỉ chấp nhận request X-FClinic-Reference-Contract: patient-doctor-reference/1. Response200/selected404 có **constant implemented marker**, không reflect tùy ý client. Verification profile v1 chọn unsupported marker→400 hoặc406, error code UNSUPPORTED_REFERENCE_CONTRACT, data:null/fieldErrors:[]; đây là PROJECT_DECIDED negotiation requirement, chưa shared adoption.
5. HTTP200 application/json, exact status/message/data wrapper, status200; tất cả11fields required kể cả nullable: id,name,specialty,department,qualification,experienceYears,consultationFee,roomNumber,bio,avatarUrl,active. Safe positive ID<=9007199254740991; strict boolean active, nullable fields phải serialize null. Preserve inactive detail200/active=false; không hide thành unrelated404.
6. Missing ID→404 exact {status:404,message:generic string,data:null,code:DOCTOR_NOT_FOUND,fieldErrors:[]}, correct marker. Dùng local provider error DTO nếu common signature chưa thay; không chỉ trả generic404 hoặc HTML. Authentication401/403 không trả Doctor data; technical errors generic/redacted.
7. Fixtures do owner chuẩn bị trên DB rỗng riêng: active/inactive/known absent IDs; credential hợp lệ và credential âm tính thật. Optional fault vectors chỉ trên controlled real binary environment, không production “fault endpoints”.

Acceptance: harness B-04 passes ACTIVE/INACTIVE/MISSING/INVALID_CREDENTIAL/WRONG_VERSION_REQUEST với real binary + recorded build/JAR/source/config hashes, actual401/403 cho credential sai, TLS valid. Fault cases absent vẫn NOT EXECUTED; không signoff full resiliency chỉ từ happy path. Cung cấp deployment evidence link binary hash→deployment, CA public trust bundle, credential secret references qua secret manager, fixture manifest, provider-side authorization tests/logs đã redact. Patient sẽ chạy harness/prod adapter, đối chiếu domain422 vs technical503; whole clinical create vẫn cần Auth/scope thật.

**Compatibility exclusion:** shared B-04 handover hiện SERVICE bearer; local consumer chọn dedicated Basic. Cần provider triển khai đúng local profile hoặc thống nhất một version kế tiếp có implementation/tests hai phía. Không tự coi hai cơ chế tương thích; không nới Patient để nhận unprotected legacy.

## 3. Task APT-REF-v1 — Appointment owner

**B-05, PAT-REF-APT-001**, independent from clinical scope authority:

1. Thêm protected read-only GET /internal/v1/appointments/{id}/medical-reference trong task Appointment riêng. Không reuse public v1/full response. Chỉ trả exact wrapper status200/message/data:{id,patientId,doctorId,status}. Không patient name/phone/reason/clinical/history/fee/date.
2. Pin [minimum schema](contracts/patient-appointment-medical-reference-v1.schema.json) SHAb2ce460335e273e227b45e982c6cefb2512d2625f6f7ff05f2342d29e34c1e86; constant request/response marker patient-appointment-medical-reference/1. TLS/dedicated Basic/least privilege projection-read/direct enforcement như DOC, credential độc lập mọi port. Unsupported marker400/406/UNSUPPORTED_REFERENCE_CONTRACT; unauthorized401/403 no resource data.
3. Migrate **own** representation end-to-end sang UUID patientId: APT-P/D/J, application command/result/port/repository/domain/events/outbox consumers và queries đang Long. UUIDv4 lowercase trên wire, patientId required/non-null, Doctor/Appointment vẫn positive safe Long. Không cast Long→UUID, random UUID per read, lookup/link theo phone hoặc generic Long fallback.
4. Cần stable persisted legacyLong↔PatientUUID mapping do Patient identity owner cung cấp khi coordinated D-15 inventory/cutover được thực thi trong task khác. Appointment owner backfill DB của mình, reconcile counts/orphans/rerun/restore trên clone, drain seed/jobs/v1 writers; không Patient ghi DB Appointment. Task hiện tại chỉ chỉ rõ prerequisite, không migration.
5. Target vocabulary CONFIRMED/CANCELLED/COMPLETED/NO_SHOW. APT-S có PENDING và domain create hiện dùng legacy state: owner phải chốt lifecycle/data disposition, retain/quarantine/reconcile legacy states có lý do. **Không PENDING→CONFIRMED, drop PENDING hoặc thay unknown bằng allowed**. Chưa normalize được→provider chưa compatible, đóng activation. Không thay vocabulary một phía để Patient decoder xanh.
6. Read repository projection authoritative đã persisted; không complete/cancel/book/reschedule, auto apply appointmentDate, tự cấp validVisit hoặc scope. NO_SHOW là well-formed reference state; không là valid encounter. Scope authority phải kiểm assignment/visit riêng, độc lập reference facts.
7. Missing404 APPOINTMENT_NOT_FOUND exact error/null/[]/marker. Valid wrong parent/doctor vẫn trả actual projection; Patient application422 sau matching, không sửa facts để khớp client. Wrong resource ID/unknown status/schema/credentials/5xx tại Patient→503.
8. Provide synthetic fixtures: matching valid, CANCELLED, known missing, different-patient, different-doctor. Optional controlled faults: wrong resource/version, PENDING/unknown, malformed,5xx. Không public fault hook hoặc test provider trong production.

Acceptance B-05: actual binary protected endpoint, verified UUID ownership/Doctor match/statuses4, unauthorized denial/nodisclosure/version/selected404, production adapter results and raw provider status pass harness. Provider persistence counts trước/sau read-only run bằng owner evidence, no mutations. Mapping/status/provider auth gaps phải giải quyết trước runtime activation, không chỉ thêm controller.

## 4. Task APT-PATIENT-CONSUMER-v2 — B-02

Provider Patient đã8v2operation, response UUID/full DTO và local auth/scope production; **Appointment consumer thực tế chưa implemented/verified**.

- Thêm trusted original-human authorization trước outbound SERVICE Patient lookup và **trước lock/slot hold hoặc bất kỳ save/history/outbox/idempotency result**. APT-A hiện lock ở58/save89/publish108, không Patient/Auth/scope port. Không khẳng định source đã có slot-hold/history/idempotency subsystem vì flow đọc không chứng minh điều đó; áp invariant cho mọi side effect khi bổ sung.
- Patient client dùng GET /api/v2/patients/{UUID}, canonical PatientResponse đầy đủ, required/null/type/status/version; reject Long/raw wrapper/malformed/timeout. Require ACTIVE; INACTIVE chặn booking theo shared business errors, không kiểm phone identity.
- Separate SERVICE bearer được introspect trusted machine subject→appointment-service phía Patient + scope appointmentProfileLookup. SERVICE token không thay quyền original human. Không đoán JWT claims hoặc lấy identity headers.
- Patient validation fail: không lock/hold/write/history/outbox/completed idempotency booking result. Nếu idempotency đã reserve in-progress thì owner phải define cleanup/failure policy; không coi failure là successful booking.
- Kiểm thử actual Appointment binary với actual Patient/Auth/scope: valid ACTIVE, missing/inactive, wrong self/scope, forged headers, Patient503/malformed; before/after counts và lock/hold events. PT Patient component không thay CT Appointment.
- Retire by-phone identity/Long APIs và reconcile consumer events trong coordinated cutover task riêng. Không triển khai M6 ở đây.

## 5. Handoff B-07 / integration lead

Patient component response samples của8operations được validate bằng Draft2020-12+format assertions, route mapping so canonical. Không chứng minh Gateway/frontend hoặc actual credentials. D-21 OAS503 thiếu7operations, safe integer/UUIDv4/lowercase encoding, permission summary và Basic-vs-bearer profiles vẫn phải ghi adoption/exclusion riêng. Correlation X-Request-Id/HTTP preservation, TLS negative tests và no disclosure free-text/logs cần real deployment tests; harness chỉ kiểm structural no-data/error redaction.

Order: Doctor protected profile → real B-04 wire tests → Appointment UUID/status/projection + B-02 consumer tasks → real B-05/B-02 → whole-chain Auth/scope/TCP → Gateway/Frontend trong M5-E/F riêng. M5-D không tự mở rộng các task đó.

