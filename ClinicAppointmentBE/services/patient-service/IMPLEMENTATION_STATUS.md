# Patient v2 — implementation và evidence

## M5-D2 Appointment UUID / protected B-05 — 24/10/2026

[New implementation/verification report](docs/integration-readiness/14_M5_D2_APPOINTMENT_PROVIDER_VERIFICATION.md); [new machine evidence](evidence/m5-d2-appointment-provider-verification.json). Appointment target now uses UUID through domain/entity/API/event, fresh validate-only PostgreSQL schema, no seed/v1 writer; protected minimum reference with dedicated Basic/TLS/effective SELECT-only role. **B-05 CODE COMPLETE / COMPONENT VERIFIED / REAL APPOINTMENT PROVIDER VERIFIED / CONTRACT VERIFIED** in the isolated selected profile:7mandatory +1actual persisted-PENDING technical vector, no mutation.

Appointment final verify BUILD SUCCESS14:01:13+07:00:104tests=45Surefire+59Failsafe (32booking TCP WITH DOUBLES+27actual-binary provider). Patient opt-in B-05 profile255=180+75, existing harness JUnit contains8vectors; default254=180+74 rerun separately and recorded in evidence. Doctor59=26+33 rerun BUILD SUCCESS13:51:50 without source edits. Patient production/test/POM hashes unchanged from M5-D1; no Patient count increase.

**B-02 PARTIAL / COMPONENT+TCP WITH TEST DOUBLES / REAL NOT VERIFIED.** Original human+self permission and full Patient UUID/ACTIVE consumer precede every slot/lock/write/outbox. Production DoctorSlot authority/delegation and idempotency/replay/operational activation gates remain; booking defaults disabled. Real Auth/scope/Gateway/Frontend NOT VERIFIED; M6 NOT EXECUTED; whole M5 NOT DONE. No approved/shared contract or existing database changed. Historical sections/evidence below remain historical snapshots.

## M5-D1 real Doctor provider — 24/10/2026

[Implementation / real B-04 evidence](docs/integration-readiness/13_M5_D1_DOCTOR_PROVIDER_VERIFICATION.md): **Doctor reference CODE COMPLETE / COMPONENT VERIFIED / REAL DOCTOR PROVIDER VERIFIED / B-04 CONTRACT VERIFIED** trên isolated actual Boot JAR/HTTPS/PostgreSQL16.10. 5mandatory vectors PASS +technical500→Patient503; actual processPID35604/dirty source manifest/full binary/config provenance, observed SELECT-only DB role10connections, unchanged before/after snapshot. Provider/PG stopped, private TLS stores removed. Không whole Patient INTEGRATION VERIFIED hoặc M5 DONE; B-05/B-02/real Auth/scope/Gateway/E2E/M6 vẫn ngoài scope/chưa verified.

Doctor full verify BUILD SUCCESS02:58:49+07:00:59tests=26Surefire+33real-binary Failsafe,F/E/S0. Patient real-provider profile BUILD SUCCESS02:58:45:255tests=180+75 (existing254 +1harness JUnit containing6vectors). Final Patient normal verify BUILD SUCCESS03:00:46:254tests=180+74,F/E/S0. Chỉ Patient pom đổi test execution để sửa proven stale-summary merge; production/all254test sources và canonical giữ nguyên. [New machine evidence](evidence/m5-d1-doctor-provider-verification.json); historical M5-D evidence và các phần bên dưới giữ nguyên. Local Basic profile đã implemented hai phía, không tự tuyên bố shared bearer proposal đã adopted.

## M5-D provider contract verification — 24/10/2026

[Actual report](docs/integration-readiness/10_M5_D_PROVIDER_CONTRACT_VERIFICATION.md), [exact provider tasks](docs/integration-readiness/11_PROVIDER_COMPATIBILITY_HANDOFF.md), [executable runbook](docs/integration-readiness/12_REAL_INTEGRATION_RUNBOOK.md): **HARNESS READY / COMPONENT VERIFIED; REAL PROVIDERS BLOCKED / NOT EXECUTED**, chưa M5-D DONE hoặc INTEGRATION VERIFIED. Doctor thiếu protected profile/TLS/machine-auth/marker/domain404; Appointment còn Long/PENDING/public DTO, thiếu minimum UUID projection. Không có configured compatible binary/credential/deployment evidence; không start legacy providers.

Final normal verify BUILD SUCCESS/exit0 lúc2026-10-24T02:24:08+07:00: **254tests=180Surefire+74Failsafe,F/E/S0**, giữ249baseline +5route/schema/preflight tests. Không thêm TCP/double;54TCP hiện có vẫn WITH STUB. Production source/config toàn bộ hash-unchanged. Chỉ POM test dependency/profile và PatientServiceIT schema assertion helper đổi; independent Draft2020-12 validator + opt-in read-only real-provider harness. Profile đã được chạy, fail đúng preflight/exit1 lúc02:21:15, outbound0; đây là BLOCKED, không provider FAIL/PASS. [Evidence M5-D](evidence/m5-d-verification.json) tách historical evidence. M5-E/F và M6 chưa thực hiện.

## M5-C reference implementation — 24/10/2026

[PAT-REF-DOC-001/PAT-REF-APT-001](docs/integration-readiness/09_M5_C_REFERENCE_IMPLEMENTATION.md): **PROJECT_DECIDED / CODE COMPLETE / COMPONENT VERIFIED / TCP VERIFIED WITH STUB / PROVIDER NOT VERIFIED / INTEGRATION NOT VERIFIED**, không M5 DONE/RELEASE READY. Cả4outbound ports có production adapters; Doctor full protected profile và Appointment minimum UUID projection được pin version/header/schema, strict missing/inactive/mismatch422 vs technical503, credentials riêng, GET only/no cache/no redirect/application retry/transaction lookup.

Final full verify `mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp` → **BUILD SUCCESS/exit0,249tests =175Surefire+74Failsafe,F/E/S0**, finished2026-10-24T01:52:26+07:00. Giữ178baseline không sửa source tests; thêm71cases gồm23TCP all4production ports với5HTTPS doubles/fresh PG16.10. Linked race201+409/1record+1revision/no orphan, fault revision HTTP500 rollback được verify. [Evidence M5-C](evidence/m5-c-verification.json) tách historical JSON.

Baseline M5-B digest expected/actual484386b057d90924ae63be7f920090db198005476e0b2aec9e3424c9b45db412, initial drift0. Chỉ PatientConfiguration đổi hai conditional fallback lines trong existing sources; PatientPolicy/ApplicationService/Writer/domain/persistence/schema/POM/Identity/Scope và toàn178tests giữ nguyên. Provider source hiện chưa protected-profile compatible; actual contract/adoption/credential/integration evidence chờ M5-D, Gateway/E2E chờ M5-E/F. Không sửa provider/shared modules, canonical artifacts, existing DB, M6 hoặc Git history. M5-B/A/audit bên dưới là historical snapshots trước M5-C.

## M5-B scope implementation — 24/10/2026

[PAT-SCOPE-001 và production scope integration](docs/integration-readiness/08_M5_B_SCOPE_IMPLEMENTATION.md): **PROJECT_DECIDED / CODE COMPLETE / COMPONENT VERIFIED / TCP VERIFIED WITH STUB / PROVIDER NOT VERIFIED**, không INTEGRATION VERIFIED hoặc M5 DONE. Administrative assignment/object/search và clinical read/create/update/valid visit tách authority/credentials; explicit current scope queries, no cache; F-06 technical scope failure→typed503, business denial403. Policy không mở thêm role/self/author quyền; port mang operation rõ ràng và controller medical POST dùng CREATE precheck.

Full verify `mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp`: **BUILD SUCCESS/exit0 lúc2026-10-24T01:22:10+07:00,178tests =127Surefire +51Failsafe,F/E/S0**. Giữ114baseline, thêm64tests; gồm19TCP mới dùng cả production identity/scope adapters, Auth/scope HTTPS doubles, fresh PostgreSQL16.10. Bốn baseline mock fixtures chỉ đổi default-delegation line; assertions/test bodies SHA-confirm unchanged ngoài dòng đó. [Evidence M5-B](evidence/m5-b-verification.json) tách historical evidence.

PatientWriter/ApplicationService/domain/persistence/schema/POM/identity production code giữ nguyên. Không authority runtime thật/source-of-truth data được tìm thấy trong repo; enable profile cần provider compatible và provisioning. M5-C Doctor/Appointment reference adapters còn fail closed; scope grant không thay reference validation. Không shared/canonical/module khác/DB cũ/M6/Git history changes. Nội dung M5-A/audit bên dưới là historical snapshot; trạng thái scope hiện tại theo mục này.

## M5-A identity implementation — 24/10/2026

[Implementation và quyết định PAT-ID-001](docs/integration-readiness/07_M5_A_IDENTITY_IMPLEMENTATION.md): **PROJECT_DECIDED HTTPS introspection; CODE COMPLETE, COMPONENT VERIFIED, TCP VERIFIED với Auth HTTPS contract double; chưa PROVIDER VERIFIED/INTEGRATION VERIFIED.** Quyền quyết định kỹ thuật M5-A được người dùng ủy quyền trực tiếp sau audit dưới đây; không tự chuyển shared contracts sang ADOPTED.

Final verify: `mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp`, **exit0 / BUILD SUCCESS lúc2026-10-24T00:46:06+07:00; 114 tests =82Surefire +32Failsafe, F/E/S0**. Giữ58tests cũ, thêm56identity/config/HTTPS/TCP tests; không suy ra coverage%. [Evidence mới và hashes](evidence/m5-a-verification.json) tách historical JSON. Năm test source cũ, PatientPolicy, writer/use cases/persistence/POM không đổi.

Production adapter opt-in kiểm current activation/issuer/audience/expiry/mappings, strict bearer/wire, server-owned exact SERVICE subject registry→serviceName (F-07), secure TLS/bounded transport. Default disabled→503; enabled config lỗi→startup fail closed; scopes/reference vẫn deny/unavailable. Test TCP dùng actual random-port Spring servlet + production identity adapter + fresh PostgreSQL16.10; Auth/scope/reference provider là test doubles. No real Auth module/runtime được tìm thấy; provider compatible/provisioning/actual mapping chưa verify. M5-B cần nguồn administrative/clinical scope thật; M5 overall và M6 chưa DONE.

Phần audit và bảng M0–M6 bên dưới là historical snapshot trước implementation M5-A. Trạng thái identity/direct TCP hiện tại được thay thế bởi mục này; không ghi đè evidence cũ.

## M5 readiness audit — 24/10/2026

[Audit và technical handoff mới](docs/integration-readiness/01_M5_READINESS_AUDIT.md): **M5 READINESS AUDITED; real integration BLOCKED, chưa M5 INTEGRATION VERIFIED hoặc release ready**. Regression mới ngày24/10/2026 00:03:29+07:00 đạt BUILD SUCCESS, vẫn38unit +20PostgreSQL/MockMvc component tests, F/E/S0; không thêm test/source changes. Sáu deliverables chứa adoption matrix, owner requests, adapter plan, test matrix và M6 prerequisites. Historical verification/evidence ngày23/10 ở phần dưới và `evidence/verification-summary.json` được giữ nguyên.

Ngày bàn giao: 23/10/2026 (Asia/Saigon). Base commit trước thay đổi: 238e1dd36cb759df444cf9441ef0012f0d12d2b5. Thay đổi đang nằm trong working tree, chưa commit/push/deploy.

**Kết quả: đã implement và verify phần core M0–M4 trên fresh PostgreSQL, chưa DONE toàn service.** M5/M6 bị chặn bởi owner contracts và coordinated migration. Không sửa approved pack, common-web, Gateway, Appointment, Auth hoặc Frontend.

## Mốc và PAT checklist

| Mốc | Trạng thái thực tế | Evidence / phần còn thiếu |
|---|---|---|
| M0 | VERIFIED cho baseline/toolchain/test setup | Java21; Maven build/test thành công. Baseline có0test, final có58test. Không đổi Boot4.1.1/Cloud2025.1.3. Docker daemon không khả dụng, thay bằng native PostgreSQL test cô lập |
| M1 / PAT-01 | IMPLEMENTED + FRESH-SCHEMA VERIFIED; PARTIAL toàn PAT-01 | UUID xuyên layers,4JPA entities/4tables, code/status/version/actors, revision; schema validate trên PG16.10. Stable upgrade mapping/clone v1/rerun/restore chưa làm do D-15 và chưa có actual DDL/data inventory |
| M2 / PAT-02 | CORE + API COMPONENT VERIFIED | 4profile APIs, normalizer/null/default/unknown fields, create-only/sharedphone, AND search/scoped count/stable page, PUT/no-op/stale/revision. Auth/mapping/permission adoption và real PT/CT còn thiếu |
| M3 / PAT-03 | CORE + API COMPONENT VERIFIED | Ports, linked/walk-in, Doctor/Appointment facts validation ngoài transaction, scope denial, parent ACTIVE lock/recheck, duplicate409; provider thật/scope authority chưa có D-05/07/19 |
| M4 / PAT-04 | CORE + API COMPONENT VERIFIED | Parent/author/current-scope, immutable refs, reason/version, medical PUT/no-op/optimistic commit check, fault rollback; real clinical security evidence vẫn BLOCKED |
| M5 | BLOCKED integration | D-01/04/05/06/07/19; chưa verifier/provider/service credential/projection adopted, chưa Gateway/Frontend real PT/CT |
| M6 | BLOCKED shared cutover | D-15; chưa clone v1/restore/stable map/consumer UUID migration/signoff/IT-12. Target binary loại v1 nhưng chưa triển khai vào môi trường dùng chung |

- [x] Code core PAT-01..04 đã có trong module và được component-test.
- [x] Bỏ Patient upsert/by-phone/v1 controller và PostConstruct seed khỏi target binary.
- [x] Bốn bảng và PostgreSQL nullable unique/local FK/version/revision path đã chuẩn bị từ approved DDL.
- [x] 8canonical operation có controller; required/type/null/enum của response được đối chiếu OAS trong tests.
- [x] Fail-closed verifier/scope/reference fallbacks; doubles chỉ ở test source.
- [x] README, integration handover và machine-readable verification summary.
- [ ] Real Auth/scope/provider/consumer/Gateway/direct TCP tests và adopted contracts.
- [ ] Clone v1 migration, persisted mapping/rerun/crash/restore và shared writer retirement thực tế.
- [ ] Workload/SLO benchmark, persistent service restart test, FE E2E.
- [ ] Full service DoD/release readiness. Không đổi trạng thái quyết định trong pack thành APPROVED/ADOPTED.

## Lệnh và kết quả cuối

Từ repository root:

~~~powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp
~~~

Kết quả lần cuối: **BUILD SUCCESS**, kết thúc 23/10/2026 23:43:29 +07:00; 38unit +20PostgreSQL/API integration tests, failures0/errors0/skipped0. verify thực sự gọi Surefire và Failsafe; executable JAR được package/repackage thành công.

| Suite | Test count | Kết quả |
|---|---:|---|
| PatientInputTest | 24 | PASS |
| PatientPolicyTest | 5 | PASS |
| PatientOrchestrationTest | 5 | PASS |
| config.PatientAuthenticationTest | 4 | PASS |
| PatientServiceIT | 20 | PASS |

Reports gốc: target/surefire-reports và target/failsafe-reports. Bản tóm tắt giữ ở [evidence/verification-summary.json](evidence/verification-summary.json), kèm hash source/config/test và canonical artifacts để phân biệt code tested với thay đổi sau này.

Lần thử đầu trong sandbox không tải được Maven Central; chạy lại với network access đã resolve/build được. Trong quá trình phát triển có lỗi biên dịch test và assertion/stubbing fault injection; đã sửa, lần cuối toàn bộ suite pass. Còn cảnh báo Mockito dynamic agent trên Java21 và API deprecated trong test helper; không disable kiểm thử hoặc đổi stack để bỏ qua.

## Môi trường và mức độ bằng chứng

- Java: Microsoft OpenJDK21.0.8. Maven theo toolchain workspace.
- Spring Boot4.1.1 / Cloud2025.1.3 theo parent hiện có; Spring/JPA context khởi động và validate schema được trong test.
- PostgreSQL16.10, database mới của cluster do test tạo trong target/patient-v2-it-*; chỉ listen127.0.0.1/cổng ngẫu nhiên; schema file V001 áp vào fresh DB. Native binary đã đóng sau test. Không đụng database/volume người dùng.
- Fixed Clock2026-10-20T03:00:00Z và clinic timezone Asia/Saigon từ fixture registry. Tiếng Việt/Unicode được round-trip trên UTF8 PostgreSQL.
- MockMvc gọi MVC stack trong process; đây không phải request qua TCP/Gateway.
- Identity, work scope, Doctor và Appointment trong IT là test doubles. Doctor/Appointment doubles còn assert không có write transaction khi lookup. Không ký JWT hoặc triển khai provider giả vào runtime.
- OAS response assertions kiểm required/type/null/enum và các semantic invariants riêng; không tuyên bố full JSON Schema validation hoặc100%coverage.

## Traceability expected/actual

| TC / tính chất | Test evidence | Actual và giới hạn |
|---|---|---|
| TC-01/02 | IT sharedPhoneCreateAndCanonicalDetail | 2UUIDv4/codes khác nhau, cùng E.164 phone,2CREATE revisions, full201/GET200; PASS component |
| TC-03/13/32 | InputTest + IT apiBindingRejects... + Unicode tests | Invalid/unknown/server field/null-gender/bad dates/no-offset/future/size lỗi; reject/no-write; optional semantics và Unicode đúng; PASS tập assertions đã viết |
| TC-04/33 UUID/error | IT constraintsAndRetiredV1 | Numeric Patient path400, unknown UUID404, old routes404; PASS component. Clone mapping TC-33 chưa chạy |
| TC-05 | IT searchCombinesFilters... | AND filter, count chỉ12allowed thay13rows, stable page5, exclude outside-scope; PASS |
| TC-06/07 | IT profileFullPut..., optionalFields..., competingProfileUpdates... | Full replacement xóa optional;1winner/1VERSION_CONFLICT; persisted revision version đúng; PASS |
| TC-08 | IT inactiveForbidsNewMedical... | New record409, old read/no-op edit được đúng policy; PASS. Booking rejection thuộc Appointment chưa chạy |
| TC-09/10/14 | IT linkedAndWalkIn... + parentExistsWithoutRecords... | Linked201,2walk-in201, chỉ1Appointment lookup; nestedpage/filter/detail full, emptyparent page200, missingparent404; PASS component |
| TC-11 | IT linkedCreateRace... | Concurrent requests201+409,1record/1revision, không orphan; PASS trên PG |
| TC-12/19 | OrchestrationTest + IT reference422VsTechnical503... | Missing/inactive Doctor hoặc mismatch422; technical error503; no-write; PASS doubles, chưa đo budget HTTP provider thật |
| TC-15/16/36 | IT parentAuthorImmutableRefsAndMedicalPut + Input/Policy tests | Wrongparent404, otherauthor403, refs request400, no-op/version0, changed/version1/reason revision, stale409; PASS |
| TC-17/18/35 | Auth/Policy tests + IT authScope.../verifierUnavailable... | Missing/invalid/inactive401, technical verifier503, forgedheaders không mở quyền, self/search/staff-clinical/missing-scope deny; PASS component. Expiry/signature/verifier thật/directTCP/Gateway chưa chạy |
| TC-20 | Medical appointment filter exercised | Filter hỗ trợ đối soát; lost-response fault scenario/end-user reconciliation chưa thực thi; PARTIAL |
| TC-23 | IT revisionFailureRollsBackBothAggregatesAndVersions | Fault revision sau entity flush: create/update cả2aggregate rollback data/version/audit; PASS PG |
| TC-29 | IT deactivateCommitBeforeCreate... + recordCommittedBeforeDeactivationRemains | Deactivate-first có controlled concurrent transactions, create409; create-first commit rồi deactivate giữrecord; PASS hai assertions. Create-first variant là sequential committed-order |
| TC-31 | IT profileFullPut.../parentAuthor.../noOpMedicalDetects... | No-op không revision, stale409, persisted revisionversion match; concurrent update làm optimistic no-op fail tại commit; PASS assertions đã viết |
| TC-21/22/24/25/26/28/30/34, IT-02/10/11/12 real | Chưa chạy | Real providers/Gateway/FE/booking/restart/clone/restore/load chưa có evidence; BLOCKED hoặc NOT EXECUTED |
| TC-27 | Không characterization v1 | Không chạy v1 trên target, hành vi upsert cũ không là yêu cầu v2 |

TC rows tổng hợp nhiều biến thể: một assertion pass không có nghĩa toàn TC/E2E đã được chứng minh. PT/CT boundary thật vẫn chưa chạy; những schema assertions trên OAS chỉ là component/provider-side checks.

## File chính

- src/main/java/com/fclinic/patientservice/api/controller/PatientController.java: 8v2routes.
- api/dto/PatientInput.java: strict request binding/normalization; PatientResponse/MedicalRecordResponse tách domain.
- application/usecase/PatientApplicationService.java: orchestration/policy/providers.
- application/usecase/PatientWriter.java: separate transactional writer, locks/flush/revisions/no-op.
- application/security/PatientPolicy.java và application/port/out/: policy/trusted context/provider boundaries.
- infrastructure/adapter/JpaPatientRepositoryAdapter.java và JpaRevisionAdapter.java: persistence và committed-version audit.
- infrastructure/entity/:4entities; resources/db/schema/V001__patient_v2_empty.sql: approved fresh-DDL copy.
- config/ và api/security/: fail-closed defaults/interceptor; application.yml: explicit v2DS/validate/no auto-init.
- src/test/java/:5test classes; pom.xml: test dependencies/resources/Failsafe, không đổi parent stack.

## Việc tiếp theo cụ thể

1. Owner chốt D-04/05/06 và cung cấp identity verifier/trusted linking; triển khai IdentityVerifierPort thật rồi chạy credential/activation/self/directTCP/Gateway tests.
2. Owner chốt D-07/19 + B-04/05 projection/authority/credentials; nối WorkScopePort/DoctorClientPort/AppointmentClientPort thật, chạy success/deny/outage/provider contracts.
3. Owner phối hợp D-01 adoption và D-21 discrepancies trong INTEGRATION_HANDOVER; không sửa approved OAS để khớp code một phía.
4. D-15: inventoryactualDDLandwriters, clone/backuprestore, upgrade/stable persisted map/rerun, consumer UUID conversion, coordinated cutover và IT-12. Chưa được start target vào oldpatient_db.

