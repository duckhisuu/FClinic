# Contract adoption matrix và boundary traceability

Ngày 24/10/2026. Đọc cùng [audit findings/evidence catalogue](01_M5_READINESS_AUDIT.md). Các status dưới là kết luận audit; **không sửa status của canonical pack**. Owner là role trong C-OWN, cá nhân/người ký/backup chưa được xác nhận.

## Artifact pins

Expected ba hash do task cung cấp đã đối chiếu actual tại filesystem. Không có mismatch; không tự chấp nhận artifact mới.

| Artefact hiện có | Version/state nguồn | Expected SHA-256 | Actual SHA-256 | Kết quả |
|---|---|---|---|---|
| [Patient OAS](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/openapi/patient.openapi.json) | 2.0.0-approved-patient-target-pack1 / APPROVED_SERVICE_TARGET nội bộ | `63e2e306a2b14d89397bb998be43f8de7b982eadf8f3b88353bc952684ebeb71` | `63e2e306a2b14d89397bb998be43f8de7b982eadf8f3b88353bc952684ebeb71` | MATCH, không là shared adoption |
| [Fixture registry](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/07_Testing_And_QA/fixtures/registry.json) | 1.0-synthetic / Clock2026-10-20T03:00:00Z | `64d6b73b655638365417bde67b70300295a01c1c1a9d22f230d8d266af33bb67` | `64d6b73b655638365417bde67b70300295a01c1c1a9d22f230d8d266af33bb67` | MATCH, fixture không cấp quyền |
| [Approved empty schema](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/04_Database_Design/schemas/patient-approved-empty-schema.sql) | Pack1.0 / approved Patient design, không upgrade v1 | `1500fb0c94d9a504237889201ce900185c771bc80403d484a1d9d47384536ef6` | `1500fb0c94d9a504237889201ce900185c771bc80403d484a1d9d47384536ef6` | MATCH |
| [Module SQL copy](../../src/main/resources/db/schema/V001__patient_v2_empty.sql) | V001 fresh-schema resource | Cùng approved SQL ở trên | `1500fb0c94d9a504237889201ce900185c771bc80403d484a1d9d47384536ef6` | MATCH |

Những artefact sau được **inventory-pin actual**, task không cung cấp expected hash riêng cho chúng. Không biến hash actual thành approved/adopted:

| Draft | Version | Actual SHA-256 | Reference cần adopt |
|---|---|---|---|
| [doctor.openapi.json](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/openapi/doctor.openapi.json) | 1.1-proposed-pack1 / PROPOSED_SHARED_TARGET | `b0128832bebd66296d11fad8e4fbce0f73b656f09e6d3827af88a724a19a25cd` | getDoctor; #/components/schemas/Doctor |
| [appointment.openapi.json](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/openapi/appointment.openapi.json) | 1.1-proposed-pack1 / PROPOSED_SHARED_TARGET | `a333df2e726aba05922b6ba68d9d5924ee56cb3af1405315cc7f1d7adb3912dc` | getAppointmentV2; #/components/schemas/Appointment; G-APPT-01 D-19 projection còn thiếu |
| [auth.openapi.json](../../../../../docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack/02_Shared_Contracts/openapi/auth.openapi.json) | 1.1-proposed-pack1 / PROPOSED_SHARED_TARGET | `55545ccc1e8cdd4aec84ea8d9a55938856c3151f7d1c23e5126b60efefc067be` | validateCaller; #/components/schemas/CallerContext |

## Decision adoption matrix

**Không có row nào ADOPTED WITH EVIDENCE** trong nguồn đã kiểm. Q-01..05 approved chỉ Patient (C-ADR:8–12), không tự quyết identity provider, scope authority hoặc consumer migration.

| Decision | Verified facts / reference | Provider / consumer owners | Approval evidence | Audit classification và blocker | Compatibility / unresolved exclusions |
|---|---|---|---|---|---|
| D-01 | C-GOV:8–23; C-B02/04/05/07:5 ghi acceptance chưa có; Patient version/hash ở trên | Mỗi provider + mọi consumer; Integration Lead kiểm freeze | MISSING: chưa có tên/ngày/chữ ký/hash acceptance | PROPOSED / NOT ADOPTED; shared integration BLOCKED | Window/cutoff chưa chốt; không dùng tên version thay chữ ký; full affected consumer list cần ký |
| D-04 | C-D:12, C-PERM:10; S-POL:19–40 chỉ STAFF + receptionist grant và per-Patient profile scope | Auth + Patient + Appointment | MISSING permission mapping/assignment nguồn | VERIFIED FROM SOURCE cho deny policy; PROPOSED / NOT ADOPTED cho shared mapping; BLOCKED | Role STAFF không auto clinical; create administrative grant và search-set/object grant phải tách; không đổi Q-02 |
| D-05 | C-D:13; C-AUTH validateCaller draft; S-ID:17–26 / S-CONF:17–27 chưa verifier/credential adapter | Auth + Gateway + tất cả resource services, Operations provisioning | MISSING verifier profile, backend caller auth, machine credential/rotation policy | VERIFIED FROM SOURCE cho fallback; MISSING production profile; BLOCKED | USER/SERVICE khác nhau; proposed HS256/design không phải lựa chọn đã ký; key/issuer/audience/expiry/activation semantics và credential transport chưa chốt |
| D-06 | C-D:14; C-AUTH CallerContext; S-CTX:6–11, S-POL:29/32–33 | Auth + Patient + Doctor + Appointment | MISSING linking authority/storage/cardinality/verification/recovery + service-name provenance | PROPOSED / NOT ADOPTED; F-07 CONFLICT; BLOCKED | Wire context có valid/userId, internal không có; internal serviceName không có trên wire draft; không tự thêm field vào closed DTO, parse subject hoặc dùng header/phone/email |
| D-07 | C-D:15; C-PERM:11–12; WorkScopePort:7–16; S-POL:42–54 | Patient + Doctor + Auth + Appointment; business owner cho valid visit | MISSING assignment/attestation source, binding/expiry/revocation/failure semantics | VERIFIED FROM SOURCE cho policy; MISSING trusted authority; BLOCKED | Linked/walk-in đều cần scope và valid visit; role/same-clinic/body id không đủ; null grant≠outage; current scope phải kiểm lại mỗi request |
| D-19 | C-D:27; C-APT G-APPT-01/getAppointmentV2; C-B05:9/13/23 | Appointment provider; Patient và Notification consumer theo phạm vi; Auth quyền service | MISSING signed minimum medical SERVICE projection/authority | PROPOSED / NOT ADOPTED + MISSING schema projection; BLOCKED | Full public DTO có reasonForVisit không mặc nhiên là approved internal DTO; không tự tạo route. Facts cần id/patient UUID/doctorId/status, valid status không được suy từ boolean false |
| D-21 | C-D:29; C-IDS:22; C-PAT paths responses; C-B07:20; S-INPUT:18/54/99–105 | Tất cả affected providers/consumers + Integration Lead | MISSING contract amendment và acceptance | CONFLICT (503/safe-range/summary); PROPOSED / NOT ADOPTED; full compliance BLOCKED | Patient OAS chỉ createMedicalRecordV2 có503, Auth outage503 áp dụng mọi operation. Safe-range/lowercase policy chưa encode hết; request Idempotency-Key của Appointment không áp Patient POST |
| D-15 | C-D:23, C-MIG:9–22; S-YML:6–22 | Mỗi DB owner + Integration Lead + Operations + consumers | MISSING actual inventory/tool/window/clone restore/rollback/signoff | PROPOSED / NOT ADOPTED; M6 BLOCKED | Không v1 writer trên UUID/shared-phone schema; no legacy adapter trong MVP; không tự chọn Flyway hoặc thời hạn tương thích |
| D-17 | C-D:25; C-SVC:10; S-APP:43–60 | Appointment + Doctor + Patient + business owner | MISSING exceptional correction/COMPLETED/NO_SHOW workflow | BLOCKED cho workflow ngoại lệ, không chặn viết core summary đã approved | Không tự complete/cancel; external status race sau snapshot không có distributed guarantee |

D-14 full Auth login/register/reset API vẫn thiếu adoption (C-AUTH G-AUTH-01), nhưng không cần tự triển khai toàn bộ auth workflows để nối verifier: cần **đủ profile validation/mapping/backend caller authorization đã signed**, không suy draft validate endpoint đã sẵn runtime. D-02/03/18 là gates của booking/schedule khi chạy full IT-02; D-08..13/16/20 phần ngoài Patient integration không được audit thành DONE.

## Boundary traceability

Chuỗi mỗi row: Contract → Provider → Consumer → Security → Runtime → Tests → Evidence → Gap → Owner action.

| Boundary / contract | Provider → Consumer thực tế | Security/trust | Runtime behavior đã thấy | Tests và evidence | Gap / owner action |
|---|---|---|---|---|---|
| B-02 / C-B02:9–19, C-PAT getPatientV2 | Patient v2 detail → Appointment booking; X-APT còn v1/Long và client snapshots | Original human authenticate+self/assisted scope **trước SERVICE lookup hoặc slot hold**; outgoing separately provisioned SERVICE; Patient chỉ trusted Appointment profile-detail grant | S-API/S-POL có provider core. X-APT:39–108 không Patient validation/human checks; lock gọi ở:55–58 | Current T-IT detail/Policy service grant là doubles. PT-B-02-getPatientV2/CT-B-02-getPatientV2 thật NOT EXECUTED | Appointment owner: UUID/full DTO decode, ACTIVE check, authorize-before-lookup/lock + no-write denial. Auth/Patient sign scope/service identity; không machine-as-human |
| B-04 / C-B04:9–19, C-DOC getDoctor | DoctorController GET /api/v1/doctors/{id} + full DoctorResponse → Patient DoctorClientPort | Authority/service credential chưa chốt D-05/07; full wire DTO phải validate trước khi lấy id/active | X-DOC có route và field active qua static read, chưa chứng minh security/error runtime. S-CONF fallback503; S-APP:46–50 mapping empty/inactive422, technical503 | T-ORCH missing/inactive/mismatch + T-IT pre-tx/no-write tests; PT-B-04-getDoctor/CT-B-04-getDoctor thật NOT EXECUTED | Doctor owner: adopt schema/error/auth, signed authority, build và fixtures; Patient tạo HTTP adapter sau đó. Không gọi provider hiện tại rồi coi protected integration pass |
| B-05 / C-B05:9–23, C-APT getAppointmentV2/G-APPT-01 | Appointment reference projection chưa có → Patient AppointmentClientPort | D-19 role-specific SERVICE projection + credential/scope; human clinical policy đã xét trước lookup | X-APT chỉ v1detail, chưa minimum v2projection. S-APP:51–60 match IDs/non-CANCELLED trước tx; S-WR ACTIVE/unique local. Không state mutation | T-IT linked/walk-in/duplicate/nested; PT-B-05-getAppointmentV2/CT-B-05-getAppointmentV2 thật NOT EXECUTED | Appointment/Auth/Patient sign exact route/projection/status/auth/error and snapshot race policy; không tự publish route một phía |
| B-07 / C-B07:9–26, C-GW:16–18 | Patient8ops → Gateway → FE | Original human bearer; no browser machine credential; direct resource security; strip forged identity headers | S-API có v2. X-GW route v1, không StripPrefix khai báo nhưng v2 root/nested chưa route/test; X-CORS không expose correlation. X-FE legacy/fallback, error loses code, chưa full PUT/version flow | T-IT MockMvc schemas/denies; PT/CT B-07 theo8operation + Gateway/TCP/FE NOT EXECUTED | Gateway owner route/header/error/CORS verification; FE owner canonical UUID/paging/fullPUT/409/lost-response/no mock success; Patient correlation work F-05 |

### B-02 required ordering

`original human verified → trusted mapping/self or receptionist scope → authorized patientId → separate SERVICE Patient lookup → validate full DTO + ACTIVE → any slot hold/local booking write`.

Nếu human bị deny: không lookup Patient bằng machine credential, không giữ slot, không tạo Appointment/history/outbox/idempotency result. Nếu Patient lookup fail: fail closed theo adopted Appointment error policy, không hold/write; profile có sẵn không bị xóa/rollback chéo DB. Current X-APT chưa đáp ứng; đây là required target, chưa là actual result.

## Adoption record cần owner điền

Mỗi B-02/04/05/07 và Auth/scope profile cần record riêng, không dùng một chữ ký chung thiếu affected consumers:

- Boundary/decision IDs; provider và mọi consumer owner **cá nhân + backup**.
- OperationId, exact API/event version, path, schema/fixture artifact URL hoặc repository path và SHA-256.
- Decision: accept/exclude/change từng discrepancy; security authority/identity source/budget policy profile version.
- Ngày, approval evidence link, compatibility window/start/end hoặc coordinated-only cutover; build versions supported.
- Provider/consumer test IDs, expected/actual evidence; unresolved exclusions và tác động; điều kiện revoke/rollback.
- **Hiện tại: các trường approval/window cá nhân đều MISSING.** Patient hash approved nội bộ không điền thay các trường này.

## Dependency graph

~~~mermaid
flowchart TD
  D01[D-01 owner and artifact adoption] --> ID[D-04/05/06 identity and service credentials]
  ID --> SCOPE[D-07 assignment and valid visit]
  ID --> REF[D-19 minimum Appointment projection]
  SCOPE --> ADAPTERS[Four production adapters]
  REF --> ADAPTERS
  D21[D-21 error/schema/budget discrepancies] --> CONTRACT[Provider and consumer contracts]
  ADAPTERS --> CONTRACT
  CONTRACT --> TCP[Direct TCP and Gateway]
  TCP --> E2E[Frontend E2E and M5 signoff]
  E2E --> M6[D-15 coordinated clone cutover gate]
~~~

Graph là phụ thuộc công việc, không xác nhận D-15 đã approved hoặc M5 signoff cho phép sửa DB. Snapshot API/provider artifact có thể bàn giao trước owner chốt các gate khác; protected adapters vẫn không được bật khi input còn thiếu.

