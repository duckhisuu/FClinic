# M5 Integration Readiness Audit

Ngày audit: **24/10/2026, Asia/Saigon**. Phạm vi: repository đang có; audit/preparation, không triển khai adapter. Trạng thái **M5 READINESS AUDITED**, không phải M5 INTEGRATION VERIFIED.

## Executive summary

**Verdict: BLOCKED cho real integration.** Core M0–M4 được bảo toàn và regression mới đạt 58 tests. Bốn production adapters vẫn là fallback fail closed; chưa tìm thấy owner adoption evidence cho bốn boundary. Chuẩn bị implementation/handoff đã sẵn ở [02](02_CONTRACT_ADOPTION_MATRIX.md), [03](03_OWNER_HANDOFF_REQUESTS.md), [04](04_M5_IMPLEMENTATION_PLAN.md), [05](05_M5_TEST_MATRIX.md), [06](06_M6_CUTOVER_PREREQUISITES.md).

| Mức sẵn sàng | Kết luận | Căn cứ |
|---|---|---|
| Core verified | Có, trong phạm vi assertions hiện có | Regression 24/10 dưới đây; S-WR/S-REPO; T-IT dùng provider doubles |
| Implementation ready | Skeleton/ports và kế hoạch có; adapter cụ thể còn BLOCKED | S-PORTS/S-CONF; D-04/05/06/07/19 chưa adopt |
| Integration verified | Chưa | Không real Auth/provider, TCP/Gateway/FE evidence; X-APT/X-GW/X-FE chưa tương thích |
| Release ready | Chưa | M5 chưa đạt, D-15/M6 chưa có clone/restore/signoff; 58 tests không là full DoD |

Làm ngay được: review và hoàn thiện adoption record, phân công owner thật, thống nhất schema/error/scope/service identity, chuẩn bị test procedures. Không có production/test code được thay đổi ở lượt audit; không thêm test hoặc coverage.

## A. Baseline thực tế

- Root xác nhận bằng Git/Resolve-Path: `D:/Hoc_tap/26_FALL/MSS301/Project_Group/FClinic`.
- Branch `main`, HEAD `238e1dd36cb759df444cf9441ef0012f0d12d2b5`; giống base commit bàn giao. Working tree chưa commit; không đổi Git state.
- `git status --short --untracked-files=all` đầu audit: **1.016 entries = 10 modified + 9 deleted + 997 untracked**. Patient có 52 entries: 19 tracked changes và 33 untracked files. Con số lớn chủ yếu do các pack/docs chưa track; không suy đây là thay đổi do audit.
- 10 tracked modified trong Patient: `pom.xml`, `PatientController.java`, `PatientResponse.java`, `GlobalExceptionHandler.java`, `PatientRepositoryPort.java`, `PatientApplicationService.java`, `Patient.java`, `JpaPatientRepositoryAdapter.java`, `JpaPatientEntity.java`, `application.yml`. Vị trí đầy đủ ở evidence catalogue/source manifest.
- 9 tracked deleted từ M0–M4: `api/dto/CreatePatientRequest.java`, `application/command/RegisterPatientCommand.java`, `application/exception/PatientNotFoundException.java`, `application/port/in/{GetPatientUseCase,ListPatientsUseCase,RegisterPatientUseCase}.java`, `application/result/PatientView.java`, `domain/repository/PatientRepository.java`, `infrastructure/persistence/SpringDataJpaPatientRepository.java`. Đây là historical deletions, không khôi phục/xóa thêm.
- Untracked Patient gồm ports/security/config/domain/medical/revision/schema mới, 5 test classes, ba tài liệu bàn giao và historical evidence. Untracked ngoài module gồm `docs/` và root pack khác; giữ nguyên.
- Pack được chọn thực sự tồn tại: `docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/`. Bản ở repository root không dùng làm canonical.
- Đối chiếu **40/40** source/config/POM/test hashes với [historical manifest](../../evidence/verification-summary.json): **0 drift**. Manifest digest historical: `6c6ddc6179cd8283295404a57f14ea10c01c6899932e94e3c8f7d06ac0b44d88`; SHA-256 của chính historical JSON: `6169a9921acb15318e0c8458c7d5ff46fcf5e901d67d6973e67321afe4ffaa5c`. Không ghi đè file này.
- Ba expected canonical hashes và SQL copy đều MATCH; expected/actual đầy đủ ở [02 § Artifact pins](02_CONTRACT_ADOPTION_MATRIX.md#artifact-pins). Hash pin không phải adoption.
- Parent POM: Java21/Boot4.1.1/Cloud2025.1.3, không đổi version. Test POM pin native PostgreSQL16.10. Không phát hiện AGENTS.md trong repository/ancestors đã kiểm.

## A3. Regression mới, không dùng lại kết quả cũ

Lệnh thực chạy từ root, không clean/reset/checkout:

~~~powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp
~~~

**Exit 0, BUILD SUCCESS, kết thúc `2026-10-24T00:03:29+07:00`, 56.097 giây.** Expected: baseline 38 unit +20 PG component, không regression; actual: đúng **58 tests, failures0/errors0/skipped0**. common-web chỉ build dependency, không sửa source và không có tests riêng. Không tuyên bố full monorepo verified.

| Suite / report mới | Tests | F/E/S | SHA-256 report ngày 24/10 |
|---|---:|---|---|
| [PatientAuthenticationTest](../../target/surefire-reports/TEST-com.fclinic.patientservice.config.PatientAuthenticationTest.xml) | 4 | 0/0/0 | `a0ebd06d3cd3733bbcc7df0f3a6fe362e193056dd9642a2e12d850614d17a610` |
| [PatientInputTest](../../target/surefire-reports/TEST-com.fclinic.patientservice.PatientInputTest.xml) | 24 | 0/0/0 | `304c5eb69ad7be77e9df1ea57c0a30ece44a0c3594ddccc067a1b37d06ff4aee` |
| [PatientOrchestrationTest](../../target/surefire-reports/TEST-com.fclinic.patientservice.PatientOrchestrationTest.xml) | 5 | 0/0/0 | `0a1b7baacb1bdc7f3fbf60f948fe1d2685f731e24ccc525c02966aa0886f479f` |
| [PatientPolicyTest](../../target/surefire-reports/TEST-com.fclinic.patientservice.PatientPolicyTest.xml) | 5 | 0/0/0 | `44ab226b8371415190d510db095ac54fc546bc4786f019e62618456f7ab86262` |
| [PatientServiceIT](../../target/failsafe-reports/TEST-com.fclinic.patientservice.PatientServiceIT.xml) | 20 | 0/0/0 | `b47f48728e88b9e8facbf3483303f1825e380fa77ce02a0ad38e7a0277b3a83d` |

Reports tại target đã được Maven tạo lại ngày 24/10; link có thể bị ghi đè khi chạy lần sau, nên hash/thời điểm ở đây là pin evidence lần audit này. Historical JSON vẫn mô tả lần 23/10; không dùng nó làm kết quả mới.

T-IT: fresh PostgreSQL16.10 native cluster trong `target/patient-v2-it-*`, loopback/cổng ngẫu nhiên, explicit target datasource và approved fresh-schema; đã stop sau test. MockMvc trong process; 4 provider ports là Mockito beans, không phải real TCP/providers. Chỉ có fixture synthetic, không token thật/secret/PHI được sao chép vào tài liệu audit. Đây không phải migration/clone test. Cảnh báo Mockito dynamic-agent vẫn có; không tắt test để che cảnh báo.

## B. Findings

Phân loại sau là kết luận audit, không sửa decision register của pack. P0 chặn protected integration; P1 phải xử lý trước signoff boundary tương ứng.

| ID | Mức | Finding / actual source | Tác động / hành động |
|---|---|---|---|
| F-01 | P0 | C-GOV:8–23, C-D:9/12–15/27/29 không có adoption record ký provider+consumer; owner mới là role, chưa gán người thật | D-01 và D-04/05/06/07/19 BLOCKED; dùng mẫu 02 và requests03, không tự adopt |
| F-02 | P0 | S-CONF:17–37 chỉ trả503 cho verifier/reference và deny scope; không có production implementation ngoài fallback trong source đã quét | Runtime không thể cho protected success; cần owner inputs trước adapter |
| F-03 | P0 | X-APT request:19–26 vẫn Long/client snapshots; service:39–108 không có Patient/Auth client và gọi lock trước booking | B-02 chưa consumer-ready: chưa chứng minh human authorize trước SERVICE lookup/slot hold, chưa validate Patient ACTIVE; owner Appointment sửa trong task riêng |
| F-04 | P0 | X-GW:10–13 route Patient v1; X-FE:350–365 by-phone/upsert + fallback id1; BookingPage:123 fallback1; api.js:248–301 booking giả khi lỗi | B-07 không thể real E2E; không bật lại v1 để chữa mismatch |
| F-05 | P1 | Không có X-Request-Id/correlation/MDC/outbound HTTP implementation trong Patient src/main đã quét; S-ID:17–27 không echo header; X-CORS không expose X-Request-Id | Chưa đạt correlation target C-GW:16–18; chuẩn bị Patient filter/context và propagation test ở04, Gateway owner phối hợp |
| F-06 | P1 | S-POL gọi WorkScopePort trực tiếp; S-ERR:41–44 biến untyped RuntimeException thành500. Không có scope-specific technical error translation | Fail closed nhưng **chưa bảo đảm503** cho scope provider outage; adapter phải map outage→typed DEPENDENCY_UNAVAILABLE, không trả null/false vì network lỗi; test bổ sung T-WS-02 |
| F-07 | P0 | S-CTX có serviceName; S-POL:32–33 yêu cầu literal appointment-service. C-AUTH #/components/schemas/CallerContext không có serviceName, có additionalProperties:false; S-CTX cũng không có userId/valid như proposed wire DTO | Chưa có trusted mapping service identity → serviceName. Không deserialize wire thẳng vào TrustedCaller, không lấy từ browser header/parse subject. Owner Auth+Patient+Appointment chốt provenance/mapping |
| F-08 | P1 | S-APORT chỉ có boolean cancelled; không thể tự phân biệt unknown/missing status với valid non-CANCELLED. DoctorReference primitive active cũng không encode missing-field error | Adapter phải validate adopted wire schema/status/required fields trước khi giảm thành internal facts. Unknown/malformed→503, legitimate missing/cancelled/mismatch→422. Không tự thêm requirement COMPLETED |
| F-09 | P1 | Patient OAS 8operations chỉ medical-create có503, trong khi S-ID và shared Auth outage503 áp dụng mọi protected operation | D-21 CONFLICT: need owner approved contract amendment/version/hash; giữ fail closed503, không sửa OAS ở audit |
| F-10 | P1 | C-B07 permission summary dùng chung nhiều operation; S-POL đúng self/admin/clinical separation. S-INPUT:18/54/99–105 áp safe integer max, OAS Patient không encode cùng maximum | Không suy receptionist clinical grant; pin operation-specific permission. Safe-range hardening đang có cần D-21 adoption, không tự bỏ/nới rule M0–M4 |
| F-11 | P1 | C-SVC:8 budgets là PROPOSED; chưa transport/client credential config. X-FE:5–27 có timeout8s và biến error thành Error(message), mất HTTP/code/fieldErrors | Không coi8s FE hay1/2/5s proposed là budget backend đã duyệt. Owner chốt chain budget/retry; FE giữ typed error để409 review |
| F-12 | P1 | T-IT:39–89 dùng MockMvc/doubles, :343–358 chỉ kiểm required/type/null/enum; không JWT expiry/signature, real TCP/Gateway, requestId hoặc whole-chain no-leak evidence | 58 tests là regression core, không full contract/security/E2E. Các khoảng trống cụ thể ở05 |
| F-13 | P1 | S-APP:43–60 lookup ngoài writer; S-WR:42–49 recheck local Patient ACTIVE. C-SVC:10 thừa nhận external Appointment có thể đổi sau lookup, D-17 còn mở | Không hứa distributed serializability; giữ non-CANCELLED/valid-visit baseline, owner chốt reconciliation/exception; không tự complete/cancel Appointment |
| F-14 | P0 release | C-MIG chưa shared tool/window/restore adoption; target yml explicit v2DS, validate/no auto-init, không upgrade script v1 | M6/IT-12 BLOCKED; schema hash bằng nhau chỉ chứng minh artefact, không chứng minh migrate thành công |

Các điểm đã VERIFIED FROM SOURCE + component regression: 8 mappings và full DTO tách domain; UUIDv4/PAT-code; scoped search count; full replacement/no-op; nested parent; author subject+doctorId; revision flush/atomic rollback; lock Patient/deactivation và nullable unique linked record. Nguồn S-API/S-DTO/S-APP/S-WR/S-REPO/S-REV/SQL; T-IT:92–303. Không có finding nào là lý do bypass deny để lấy success.

## M5 gate verdict và việc có thể làm ngay

- **BLOCKED**: real adapters, provider/consumer adoption, TCP/Gateway/FE verification; chưa có ADOPTED WITH EVIDENCE cho các boundary.
- **Chuẩn bị được ngay**: owner requests03; test matrix05; chọn người ký/compatibility/exclusions; review correlation và typed scope-error design04; giữ core regression.
- **Lượt code đầu sau đủ inputs**: M5-A identity adapter + wire DTO validation + human/SERVICE mapping, rồi direct TCP denial/outage tests theo04. D-05 strategy và D-06 trusted mapping phải được ký trước; không chọn proposed validate endpoint chỉ vì nó có trong draft OAS.
- Không thay production/test code/POM/config; không migration, không gửi owner requests ra bên ngoài. M5 và M6 vẫn chưa DONE.

## Evidence catalogue

Các ID này dùng chung cho cả sáu tài liệu; line numbers là one-based tại snapshot audit. JSON dùng JSON Pointer/operationId để tránh suy theo prose summary.

| ID | File thật / reference |
|---|---|
| S-API | [PatientController](../../src/main/java/com/fclinic/patientservice/api/controller/PatientController.java):14–74 |
| S-DTO | [PatientResponse](../../src/main/java/com/fclinic/patientservice/api/dto/PatientResponse.java):9–14; [MedicalRecordResponse](../../src/main/java/com/fclinic/patientservice/api/dto/MedicalRecordResponse.java):6–12 |
| S-INPUT | [PatientInput](../../src/main/java/com/fclinic/patientservice/api/dto/PatientInput.java):24–54,65–105 |
| S-APP | [PatientApplicationService](../../src/main/java/com/fclinic/patientservice/application/usecase/PatientApplicationService.java):26–83 |
| S-WR | [PatientWriter](../../src/main/java/com/fclinic/patientservice/application/usecase/PatientWriter.java):23–67 |
| S-POL | [PatientPolicy](../../src/main/java/com/fclinic/patientservice/application/security/PatientPolicy.java):15–56 |
| S-CTX | [TrustedCaller](../../src/main/java/com/fclinic/patientservice/application/security/TrustedCaller.java):5–12 |
| S-ID | [PatientAuthentication](../../src/main/java/com/fclinic/patientservice/api/security/PatientAuthentication.java):17–27 |
| S-CONF | [PatientConfiguration](../../src/main/java/com/fclinic/patientservice/config/PatientConfiguration.java):17–37; [PatientWebConfiguration](../../src/main/java/com/fclinic/patientservice/config/PatientWebConfiguration.java):11–12 |
| S-PORTS | [IdentityVerifierPort](../../src/main/java/com/fclinic/patientservice/application/port/out/IdentityVerifierPort.java):3–5; [WorkScopePort](../../src/main/java/com/fclinic/patientservice/application/port/out/WorkScopePort.java):7–16 |
| S-DPORT / S-APORT | [DoctorClientPort](../../src/main/java/com/fclinic/patientservice/application/port/out/DoctorClientPort.java):3–5; [AppointmentClientPort](../../src/main/java/com/fclinic/patientservice/application/port/out/AppointmentClientPort.java):4–7 |
| S-REPO / S-REV | [JpaPatientRepositoryAdapter](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/JpaPatientRepositoryAdapter.java):19–91; [JpaRevisionAdapter](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/JpaRevisionAdapter.java):15–36 |
| S-ERR / S-YML | [GlobalExceptionHandler](../../src/main/java/com/fclinic/patientservice/api/exception/GlobalExceptionHandler.java):15–44; [application.yml](../../src/main/resources/application.yml):6–40 |
| SQL | [V001](../../src/main/resources/db/schema/V001__patient_v2_empty.sql):2–66; approved source/hash ở02 |
| T-IT | [PatientServiceIT](../../src/test/java/com/fclinic/patientservice/PatientServiceIT.java):39–89,92–303,343–386 |
| T-INPUT | [PatientInputTest](../../src/test/java/com/fclinic/patientservice/PatientInputTest.java): normalizer/date/PUT/strict-fields tests |
| T-POL | [PatientPolicyTest](../../src/test/java/com/fclinic/patientservice/PatientPolicyTest.java):18–57 |
| T-ORCH | [PatientOrchestrationTest](../../src/test/java/com/fclinic/patientservice/PatientOrchestrationTest.java):24–62 |
| T-AUTH | [PatientAuthenticationTest](../../src/test/java/com/fclinic/patientservice/config/PatientAuthenticationTest.java):14–40 |
| X-APT | [BookAppointmentRequest](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/api/dto/BookAppointmentRequest.java):19–51; [AppointmentApplicationService](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/application/usecase/AppointmentApplicationService.java):39–108; [AppointmentController](../../../appointment-service/src/main/java/com/fclinic/appointmentservice/api/controller/AppointmentController.java):24–83 |
| X-DOC | [DoctorController](../../../doctor-service/src/main/java/com/fclinic/doctorservice/api/controller/DoctorController.java):25–49; [DoctorResponse](../../../doctor-service/src/main/java/com/fclinic/doctorservice/api/dto/DoctorResponse.java):5–30 |
| X-GW / X-CORS | [Gateway routes](../../../../infra/api-gateway/src/main/resources/application.yml):10–25; [CorsConfig](../../../../infra/api-gateway/src/main/java/com/fclinic/apigateway/config/CorsConfig.java):18–25 |
| X-FE | [api.js](../../../../../ClinicAppointmentFE/src/services/api.js):5–27,248–301,350–365; [BookingPage](../../../../../ClinicAppointmentFE/src/pages/BookingPage.jsx):114–145 |
| C-GOV / C-D | [Governance](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/Contract_Governance.md):8–25; [Decision register](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/09_Decisions_And_Audit/Open_Decisions_And_Blockers.md):9–29 |
| C-PERM / C-SEC | [Permission matrix](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/Authentication_Authorization.md):9–30; [Patient security](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/03_Service_Specifications/Patient_Service/SECURITY_RULES.md):5–11 |
| C-ERR / C-SVC | [Shared errors](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/Error_Handling_Contract.md):7–22; [S2S communication](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/Service_To_Service_Communication.md):6–14 |
| C-IDS / C-GW | [Identifiers](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/Identifier_And_Data_Standards.md):8–24; [Target Gateway rules](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/06_Integration_And_Deployment/Gateway_Routing.md):7–18 |
| C-MIG / C-IT | [Migration strategy](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/04_Database_Design/Database_Migration_Strategy.md):9–22; [Integration cases](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/07_Testing_And_QA/Integration_Test_Plan.md):28–42,156–201 |
| C-OWN / C-ADR | [Team ownership](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/08_Team_Implementation_Plan/Team_Ownership_Matrix.md):7–15; [Q-01..05](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/09_Decisions_And_Audit/Architecture_Decision_Records.md):8–12 |
| C-AUTH | [Auth draft OAS](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/openapi/auth.openapi.json): /paths/~1api~1v1~1auth~1validate; /components/schemas/CallerContext (line471+) |
| C-PAT / C-DOC / C-APT | [Patient OAS](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/openapi/patient.openapi.json); [Doctor OAS](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/openapi/doctor.openapi.json); [Appointment OAS](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/openapi/appointment.openapi.json); versions/hash/pointers ở02 |
| C-B02 / C-B04 / C-B05 / C-B07 | [B-02](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/handovers/B-02.md):5–19; [B-04](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/handovers/B-04.md):5–19; [B-05](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/handovers/B-05.md):5–23; [B-07](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/handovers/B-07.md):5–26 |
| C-MATRIX | [Provider/consumer matrix](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/Provider_Consumer_Matrix.md): B-02/04/05/07 rows; summary không thay quyền từng operation |
| S-PARENT / historical | [Parent POM](../../../../pom.xml):10,22–38; [historical status](../../IMPLEMENTATION_STATUS.md); [historical verification](../../evidence/verification-summary.json) |

## Quality gate closure

Sáu deliverables ở cùng thư mục; chỉ thêm liên kết ngắn vào historical status. Source/test/config/POM và historical verification hashes giữ nguyên; pack và shared source tree fingerprints được đối chiếu trước/sau. Regression chỉ tái chạy suite hiện có, không tăng coverage/test count. Mọi test tương lai ghi PLANNED/BLOCKED; mọi owner request là draft chưa gửi. Không copy token, credential, HTTP auth payload hoặc clinical content vào audit. Không thay Git history hoặc chạy M6.


### Kiểm tra cuối sau khi tạo deliverables

- 6/6 tài liệu Markdown được tạo, code fences cân bằng, không replacement character; **90 local Markdown links đều resolve tới file tồn tại**. Future implementation paths trong04 chỉ là đề xuất, không được trình bày như source đã có.
- Git status inventory: 1016→1022 entries, tăng đúng6 tài liệu mới; historical IMPLEMENTATION_STATUS.md (đã untracked từ trước) chỉ thêm mục liên kết audit. HEAD/branch giữ nguyên. Không đổi source/test/POM/config;40 historical source entries vẫn match từng SHA.
- Các tree fingerprints dưới đây so trước/sau **bằng nhau**. Cách tính cùng môi trường: git ls-files -co --exclude-standard → PowerShell Sort-Object -Unique → chỉ file tồn tại theo prefix → mỗi row path=UPPERCASE-SHA256(file), join bằng LF không trailing LF → SHA-256 UTF8. Đây là integrity comparison, không phải Git commit hoặc owner approval.

| Protected prefix/file | Files | Before = after SHA-256 tree fingerprint | Result |
|---|---:|---|---|
| `ClinicAppointmentBE/services/patient-service/src/` | 39 | `5788fe968a2418ede7922777f8eb4f30f50672e2e8cb64d50dc7127e3ce8da24` | SAME |
| `ClinicAppointmentBE/services/patient-service/pom.xml` | 1 | `873fcc0dc357e011ee590620177ed90984ea74e28a1dd55d0915a0917b54ad2a` | SAME |
| `ClinicAppointmentBE/common/` | 8 | `f01ac2f408aec9f5c51594ffb9a89b585a7a874aae6ecabc5adc78eec2929d23` | SAME |
| `ClinicAppointmentBE/infra/` | 7 | `384c42f3847d51459095d8b0c8656a705f961bc8319c4076f4d6eb2137c11817` | SAME |
| `ClinicAppointmentBE/services/doctor-service/` | 32 | `99a781e90e6092c6de2312931ed26acce65d89d0864b9bc9b0cd3271174e0dd2` | SAME |
| `ClinicAppointmentBE/services/appointment-service/` | 39 | `4190e6bf19427b193a12f3a526f3da1747512399a3c031dd012a6a8fac82c489` | SAME |
| `ClinicAppointmentFE/` | 19 | `5e934a3097b48c5d77ad477b1d6c396ec7b08547df9b943f62ddc49c66e8dbfc` | SAME |
| `docs/FClinic_Implementation_Ready_Pack/` | 454 | `fba3408ec1cd17b58960df60b6b7f2f753e798175fe51e7e01ff770cf910bc77` | SAME |
| `ClinicAppointmentBE/pom.xml` | 1 | `a490de79e8f95ac97d81cee8b8dbc872e1d2b58046e748768cfda7866119b937` | SAME |
| `ClinicAppointmentBE/services/patient-service/evidence/verification-summary.json` | 1 | `92f06eb0d2d895edc85c52176be688d18b882fa4531b475d9fb014b40c516678` | SAME |

Historical JSON actual-file SHA-256 được ghi riêng ở baseline; tree fingerprint là hash có path/row framing nên không so trực tiếp với raw file SHA. Reports mới chỉ pin bằng report SHA ở A3; không hash-report thay cho test result.


