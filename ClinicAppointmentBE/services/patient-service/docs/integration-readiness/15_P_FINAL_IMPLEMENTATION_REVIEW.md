# P-FINAL — Patient implementation review

26/10/2026, Asia/Saigon. Phạm vi chốt: **Patient core + production consumers**, không triển khai Auth/WorkScope provider hoặc M6. Kết quả chạy và acceptance cuối ở [19](19_P_FINAL_ACCEPTANCE_REPORT.md), full manifests/reports ở [machine evidence](../../evidence/patient-p-final-verification.json). Không thay kết quả lịch sử M5-A…D2.

## Baseline và thay đổi

Original main/HEAD `238e1dd36cb759df444cf9441ef0012f0d12d2b5`;66 tracked changes,1234 untracked trước phiên, không staged. Backup per-file đã verify tại `D:\FClinic_SafeSync\patient-p-final-20261026-085656-570bd1cc`. Nhánh GitHub bao/commit6a55548 của lần trước không được sửa/push trong P-FINAL.

Không tìm thấy lỗi chức năng cần sửa production trong phạm vi đã review. Giữ nguyên toàn bộ src/main và POM. Bổ sung **3 tests** trong [PatientIdentityTcpIT](../../src/test/java/com/fclinic/patientservice/identity/PatientIdentityTcpIT.java):

- `invalidAuthContractProtectsEveryMutationAndAllPersistedContent`: wrong contractVersion, missing required mapping field và malformed JSON đều503 trước4mutation routes; so sánh toàn bộ persisted JSON rows của4tables và không gọi scope/reference.
- `inactiveIntrospectionBlocksPreviouslyAllowedNoOpAndChangedPuts`: sau no-op profile/medical được phép, double chuyển cùng bearer sang active:false; no-op và changed PUT đều401, không thay nội dung/audit/version/revision. Đây chỉ mô phỏng **wire result** của revocation, không chứng minh provider token lifecycle.
- `authDeadlinePreventsAdministrativeAndClinicalWrites`: HTTPS Auth double bị giữ bằng latch; profile/medical POST cùng nhận503 theo deadline; DB nguyên vẹn và không gọi scope/reference. Không dùng timing sleep hoặc permissive runtime.

SHA-256 test file `57c275a670e385ffffc09fe8d25d928e0283a9cbd54ce5d146b028a63d008df6`; production+POM manifest `b8c57ca9460e31c15f71f029583b24554766b0168afad5d99c0b82513c1392c1`; test-source manifest `e8b3b9ea40f20f510433e82a3ab3ec8eeaea15f352ce456a34af4991e3ed667e`. Algorithm: SHA256 UTF8 of sorted repository-relative paths, slash-normalized + space + fileSHA, joinedLF/no trailingLF. Full ordered paths/digests in machine evidence, dirty state is not represented by HEAD alone.

## Eight operations và invariants

| Contract / operation | Source reviewed | Evidence / practical limit |
|---|---|---|
| POST `/api/v2/patients` | [Controller](../../src/main/java/com/fclinic/patientservice/api/controller/PatientController.java), [PatientInput](../../src/main/java/com/fclinic/patientservice/api/dto/PatientInput.java), [Patient](../../src/main/java/com/fclinic/patientservice/domain/aggregate/Patient.java), [Writer](../../src/main/java/com/fclinic/patientservice/application/usecase/PatientWriter.java) | UUIDv4 application-generated, immutable PAT-code; create-only shared phone; ACTIVE/version0; trusted actor. PatientServiceIT tc01/tc02, input tests. |
| GET collection | Input#query, [repository](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/JpaPatientRepositoryAdapter.java)#search/page, [Policy](../../src/main/java/com/fclinic/patientservice/application/security/PatientPolicy.java)#search | keyword AND normalized phone, scoped count, empty authorized result, page0/size10/max100, whitelisted sort + id same direction. ServiceIT tc05 + ScopeTcp search tests. |
| GET `/{id}` | Policy#readProfile, application#get, response DTO | USER self or independent administrative/clinical/SERVICE grant. Full required/nullable response. Identity/Scope TCP; SERVICE cannot inherit human rights. |
| PUT `/{id}` | Input#profile/version, Policy#replaceProfile, Writer#replace | Full replacement/null clearing, stale409 before equality, no-op unchanged actor/time/version/revision with commit check. Scope checked on no-op; PG tc06/tc31 and current-scope revocation tests. |
| POST `/{patientId}/medical-records` | [Application](../../src/main/java/com/fclinic/patientservice/application/usecase/PatientApplicationService.java)#createRecord, Writer#createRecord | Doctor subject/mapping/scope/valid visit; pretransaction reference lookup; ACTIVE lock/recheck; walk-in skips only Appointment; linked nullable unique guards race. tc09/10/11/19/29 and reference TCP races. |
| GET medical collection | Policy#readMedical, application#records, repository#records | Parent-aware lookup, empty page for existing parent; appointmentId filter; visitDate desc/id desc default. tc14 and canonical response assertions. |
| GET medical detail | application#record + repository#findRecord | Wrong nested parent404 after authorization; inactive parent still readable by allowed caller. tc15/16 and TCP nested checks. |
| PUT medical detail | Policy#editMedical, Writer#replaceRecord | Current scope + immutable author subject/doctor; full optional clearing, version/changeReason even no-op; no remote reference lookup; persisted flush/revision atomic. tc16/23/31/36. |

[PatientProviderContractTest](../../src/test/java/com/fclinic/patientservice/contract/PatientProviderContractTest.java) compares all8 runtime paths/methods with pinned OAS; [PatientServiceIT](../../src/test/java/com/fclinic/patientservice/PatientServiceIT.java) validates representative8operation responses with Draft2020-12 schema. This is **not exhaustive HTTP/OpenAPI/security compliance** or100%coverage.

## Security, transactions và privacy

- [Authentication](../../src/main/java/com/fclinic/patientservice/api/security/PatientAuthentication.java) enforces direct v2 port, strict single Bearer; only verified context becomes request attribute. Missing/invalid401, authenticated denial403, dependency failure503. Browser identity/scope headers are not grants.
- [Introspection decoder](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/identity/IntrospectionDecoder.java) freezes exact11fields/version/issuer/audience/expiry/current assertion and USER/SERVICE mapping boundaries. No issuer or token generator in Patient. [Auth handoff](16_P_FINAL_AUTH_CONSUMER_HANDOFF.md).
- [Scope adapter](../../src/main/java/com/fclinic/patientservice/infrastructure/adapter/scope/RemoteWorkScopeAdapter.java) routes admin/clinical separately, binds caller/resource/operation/visit, no positive cache. Denial403 versus unavailable503; empty authorized set differs from no grant. [Scope handoff](17_P_FINAL_WORKSCOPE_HANDOFF.md).
- Separate proxied Writer transactions, local pessimistic/optimistic locks and flush-before-revision remain intact; PG latch/barrier tests cover duplicate linked create, deactivate/create ordering, competing updates, no-op commit conflict and revision rollback. Remote snapshot/decision races after the last authorization check remain distributed-system limits.
- [Error handler](../../src/main/java/com/fclinic/patientservice/api/exception/GlobalExceptionHandler.java) emits generic canonical envelope, maps only selected appointment unique constraint to duplicate409. No raw SQL/exception/token/clinical value is returned. Application routine source does not log bodies/tokens; deployment/APM/access/DB logging still needs operator redaction.
- [Runtime config](../../src/main/resources/application.yml): explicit v2 datasource, `ddl-auto:validate`, SQL auto-init off, no seed/v1/by-phone/upsert. Approved [fresh schema](../../src/main/resources/db/schema/V001__patient_v2_empty.sql) is not a legacy migration. No shared DB used.

## Preserved limitations, not hidden acceptance

D-21 remains: OAS503 coverage, permission-summary wording and safe JSON integer shared adoption. F-05 request correlation propagation/telemetry still needs cross-service deployment work; no new requestId propagation claim. Auth/scope trustworthy issuance, live activation/revocation and actual authority data are pending. Inbound shared deployment must provision TLS/network controls, redacted observability and capacity budgets; loopback HTTP servlet tests are not production TLS proof. Gateway/Frontend, B-02 whole-chain, load/operational rotation, D-15/M6 remain separate gates. These do not require replacing Patient core or weakening its strict consumer contracts.
