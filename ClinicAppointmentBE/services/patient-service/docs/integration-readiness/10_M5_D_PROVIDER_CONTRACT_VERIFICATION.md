# M5-D — Real provider contract verification

Ngày 24/10/2026, Asia/Saigon. **M5-D: BLOCKED real-provider verification; harness và Patient component conformance VERIFIED trong phạm vi dưới đây. Không M5-D DONE, không whole-chain INTEGRATION VERIFIED/RELEASE READY.**

## 1. Baseline và discovery

- Branch main, HEAD238e1dd36cb759df444cf9441ef0012f0d12d2b5; giữ working tree chưa commit. Không reset/checkout/clean/commit/push.
- Pre-edit84entry source/test/config/POM manifest **MATCH** expected/actual1924f0e22fd5de78a9b288d07cd26df262b75bbf8b8ad24def84f534cf96eb17; drift0. [Historical M5-C](../../evidence/m5-c-verification.json)249tests175Surefire+74Failsafe,54TCP with doubles là kết quả cũ, không giả làm run mới.
- Seven provider/common source snapshots trong M5-C vẫn hash-identical. Không sửa production Patient source/config, providers, common-web hoặc approved pack.
- [Read-only discovery](../../scripts/Discover-RealProviders.ps1):9required real-provider env settings absent; Doctor/Appointment target không có executable JAR. Không có configured isolated compatible endpoint/credential/deployment evidence. Không quét/guess remote endpoint, start legacy binary/seed, tắt TLS hoặc chạm shared DB. Kết luận này không khẳng định không tồn tại provider ở mọi môi trường bên ngoài repo.
- Canonical actual pack: docs/FClinic_Implementation_Ready_Pack/FClinic_Implementation_Ready_Pack. Patient OAS2.0.0-approved-patient-target-pack1 SHA63e2e306a2b14d89397bb998be43f8de7b982eadf8f3b88353bc952684ebeb71; registry64d6b73b655638365417bde67b70300295a01c1c1a9d22f230d8d266af33bb67; emptySQL1500fb0c94d9a504237889201ce900185c771bc80403d484a1d9d47384536ef6. All MATCH, không là adoption evidence.

## 2. Provider compatibility thực tế

Source anchors/line và exact implementation tasks ở [11 handoff](11_PROVIDER_COMPATIBILITY_HANDOFF.md#1-source-anchors-đã-kiểm-tra); hashes ở [M5-D evidence](../../evidence/m5-d-verification.json).

| Boundary | Consumer selected | Observed provider source | Runtime result |
|---|---|---|---|
| B-04 Doctor→Patient | PAT-REF-DOC-001, patient-doctor-reference/1, GET /api/v1/doctors/{id}; protected full11field/constant marker/domain404 | DOC-C/D success shape matches declared fields; DOC-A preserves inactive detail; DOC-E generic3field404. No matching machine auth/TLS/marker in src/main/config; legacy auto-seed/ddl-update | **PROVIDER BLOCKED / NOT EXECUTED**; not compatible protected profile from source, no real binary/config to test |
| B-05 Appointment→Patient | PAT-REF-APT-001, patient-appointment-medical-reference/1; GET /internal/v1/appointments/{id}/medical-reference | APT-C/D/J publicv1/Long patient/full PHI DTO; minimum route absent; APT-S PENDING, no NO_SHOW; no protected reference profile | **PROVIDER BLOCKED / NOT EXECUTED**; UUID/projection/status/authentication missing |
| B-02 Patient→Appointment | Canonical PatientResponse UUID/full DTO; SERVICE detail scoped after original human authorization | APT-P/J/A stillLong/client snapshots/no Patient verification dependency; lock58/save89/publish108 before any shown human/Patient validation | Patient provider component verified subset; actual Appointment CT **BLOCKED / NOT EXECUTED** |
| B-07 Patient→Gateway→Frontend | Eight canonicaloperations, full PUT/errors/page/security | Patient component evidence below; Gateway/FE unchanged/not exercised | Component only; actual consumer adoption/TCP/Gateway/E2E **NOT EXECUTED** |

Doctor local full schema SHA098d3651fe4bc5daad3ec813ce459acfc624a0888ad209f789aa6a4bcd08c172; Appointment minimum SHAb2ce460335e273e227b45e982c6cefb2512d2625f6f7ff05f2342d29e34c1e86, both unchanged PROJECT_DECIDED. Draft Doctor/Appointment OAS1.1-proposed-pack1 không là implemented/adopted runtime; shared handovers SERVICE bearer khác selected local Basic. Không lặng lẽ map PENDING/Long hoặc nới decoder.

## 3. Executable verification, không thêm provider double

| File | Scope |
|---|---|
| [RealProviderContractCheck](../../src/test/java/com/fclinic/patientservice/contract/RealProviderContractCheck.java) | Explicit profile only, actual GET/strict schema/version/error and production adapters; redacted expected/actual case report; no Spring/DB/mutations |
| [ProviderContractPlan](../../src/test/java/com/fclinic/patientservice/contract/ProviderContractPlan.java) | Strict preflight/synthetic vectors/positive-safe IDs, executable provider JAR/controller/hash + external deployment evidence, env secret references |
| [ProviderProbe](../../src/test/java/com/fclinic/patientservice/contract/ProviderProbe.java) | Test-only HTTPS client, not a server/mock; raw401/403/5xx observations before production503 translation, chain/hostname verification,1sconnect/3sdeadline/64KiB/no redirects/retries |
| [ContractSchemas](../../src/test/java/com/fclinic/patientservice/contract/ContractSchemas.java) | Independent Draft2020-12 schema validation/format assertions against pinned local/canonical artifacts, generic failure count/no instance data |
| [pom](../../pom.xml) | Test-only networknt3.0.8/Jackson3 dependency; local schema resources; profile real-provider-contracts with separate Failsafe reports/summary, normal verify excludes harness by name |
| [ProviderContractPreflightTest](../../src/test/java/com/fclinic/patientservice/contract/ProviderContractPreflightTest.java), [PatientProviderContractTest](../../src/test/java/com/fclinic/patientservice/contract/PatientProviderContractTest.java) | 3safe preflight unit cases +2route/schema cases; no fixture server or external requests |

Validator selection follows [upstream Jackson3 usage](https://github.com/networknt/json-schema-validator#usage). Test scope only; no parent stack/JAR runtime dependency change. OAS components copied into validation document $defs with local $ref rewrite; original pack untouched, no remote schema retrieval. OpenAPI annotation example may warn as unknown nonvalidation keyword; bounds/type/null/pattern/format are asserted. Not a complete HTTP OpenAPI validator over every input/status/security vector.

Harness requires REAL_BINARY deployment evidence tied to an inspected executable provider JAR and source/config/endpoint hashes. **Local JAR hash plus operator attestation is not cryptographic proof of remote runtime identity**; reviewer must inspect actual deployment mapping before accepting REAL PROVIDER PASS. No generator/mock may be attested. Missing controlled-fault fixtures recorded NOT EXECUTED; no JUnit skipped substitutes.

Verification extension PROJECT_DECIDED: unsupported request marker400/406/UNSUPPORTED_REFERENCE_CONTRACT/no resource data, for provider negotiation test; not silently added to canonical pack or claimed provider implemented. [Templates and detailed runbook](12_REAL_INTEGRATION_RUNBOOK.md) explain current mandatory5Doctor/7Appointment vectors and optional faults.

## 4. Actual execution ledger

| Command / scenario | Expected | Actual |
|---|---|---|
| mvn ... test, initial validator compile | Compile new helper | BUILD FAILURE02:14:52, wrong Dialects import in new helper; fixed package, no baseline source semantics changed |
| mvn ... test, initial new assertion | Schema test | BUILD FAILURE02:17:31,177tests/1failure: incorrect assumption Error rejects extra fields; assertion corrected to actual canonical constraints, pack untouched |
| Full verify intermediate | Baseline249 +2new | BUILD SUCCESS02:19:31,251tests, F/E/S0 |
| Full verify -P real-provider-contracts | Regression passes; absent provider config fails preflight before network | BUILD FAILURE/exit1 **expected guard result**02:21:15;254regressionPASS plus1preflightFAILED, cases[], outbound0. Classification BLOCKED; **zero real-provider cases executed** |
| Final full normal verify | All249baseline +5new, zeroF/E/S, package | **BUILD SUCCESS/exit0,254=180Surefire+74Failsafe,F/E/S0,finished2026-10-24T02:24:08+07:00,50.428s** |

Exact normal command:
~~~powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp
~~~

Final baseline suite counts individually match all M5-C suites; existing tests preserved, only PatientServiceIT schema helper strengthened and medical PUT response assertion added. No tests skipped/disabled. Four fresh native PostgreSQL16.10 clusters per full run, random loopback ports/approved empty SQL, shutdown after run. No H2/shared DB. Existing54direct TCP tests remain **STUB TCP PASS**, no new TCP tests/servers. No coverage percentage claim. Current target reports are mutable rerun artifacts; evidence pins hashes/rows, historical evidence files preserved.

## 5. D4 — Patient provider conformance trace

[Controller](../../src/main/java/com/fclinic/patientservice/api/controller/PatientController.java):14–84; [PatientServiceIT](../../src/test/java/com/fclinic/patientservice/PatientServiceIT.java)20cases; [input unit tests](../../src/test/java/com/fclinic/patientservice/PatientInputTest.java)24; policy/orchestration tests and54historical TCP cases rerun.

| Canonical operation | Component evidence / response full-schema assertion | Classification |
|---|---|---|
| POST /api/v2/patients | tc01_tc02, createHttp helper309; PatientCreated/UUIDv4/PAT code/sharedphone, required nullable output | COMPONENT PASS sampled scenarios |
| GET /api/v2/patients | tc05_search...103; PatientPageResponse/AND filters/scoped count/stable page | COMPONENT PASS sampled scenarios |
| GET /api/v2/patients/{id} | tc01_tc02...92; PatientResponse, required/null/normalizedphone | COMPONENT PASS sampled scenarios |
| PUT /api/v2/patients/{id} | tc06_tc31...118; PatientResponse, full replace/no-op/stale/revision; tc06 optional deletion | COMPONENT PASS sampled scenarios |
| POST /api/v2/patients/{patientId}/medical-records | tc09_tc10_tc14...142/createMedicalHttp313; MedicalRecordCreated/linked/walk-in | COMPONENT PASS sampled scenarios |
| GET /api/v2/patients/{patientId}/medical-records | tc09...151; MedicalRecordPageResponse/appointment filter/empty parent tc14 | COMPONENT PASS sampled scenarios |
| GET /api/v2/patients/{patientId}/medical-records/{recordId} | tc09...154; MedicalRecordResponse/nested404 tc15 | COMPONENT PASS sampled scenarios |
| PUT /api/v2/patients/{patientId}/medical-records/{recordId} | tc15_tc16_tc36...157, putMedical325; MedicalRecordResponse/Error, version/reason/author/immutable/no-op | COMPONENT PASS sampled scenarios |

New reflection test checks exact8path+method set against actual pinned OAS. Full validator replaces former required/type/null/enum-only helper; all listed samples validate referenced schemas, patterns/bounds/formats/nullable/structured fieldErrors. New validator guard checks invalid phone/name length/date and fieldErrors type rejected. Canonical Error does not close additionalProperties, so tests do not invent such constraint.

Request omission/null/unknown/fullPUT/query rules remain PatientInput/body whitelist/unit+existing API tests; not an independent exhaustive input validator. Clinical permissions verified locally/doubles only, not actual authority. **Not covered:** every HTTP request/response/error branch, all OAS examples, provider auth/TLS fault deployment, real B-02 consumer/B-07 Gateway/Frontend, privacy free-text/log inspection, requestId propagation.

**D-21 known discrepancy, not contract conformant claim:** OAS declares503 only medical create, shared Auth-unavailable requires503 for all8; additional UUIDv4/lowercase/safeLong policy not fully encoded canonical schema, summaries do not define all current scope rules. Local reference Basic profile differs shared proposed SERVICE bearer. No canonical edits/adoption assertion. Provider conformance does not mean entire public/security contract fully verified.

## 6. Contract vectors and real results

| PT/CT / matrix | Executable vectors | Actual this session |
|---|---|---|
| PT/CT-B-04-getDoctor; T-REF-01/02 | ACTIVE/INACTIVE/MISSING,INVALID_CREDENTIAL,WRONG_VERSION_REQUEST; optional wrongresource/version/payload/5xx | **BLOCKED / NOT EXECUTED**, actual provider build/version null |
| PT/CT-B-05-selected minimum; T-REF-03/04/05 | VALID/CANCELLED/MISSING/PATIENT_MISMATCH/DOCTOR_MISMATCH,invalidcredential/wrongversion; optional unknown/PENDING/malformed/wrongresource/5xx | **BLOCKED / NOT EXECUTED**, actual provider build/version null |
| T-REF-06 | Existing production transaction guards/order/walk-in tests rerun | STUB TCP PASS subset; real order/requestId/no-write NOT EXECUTED |
| PT-B-02/07 response subset | Full schema/assert route matrix above | COMPONENT PASS; CT actual consumer NOT EXECUTED |
| IT-02/10/11/12 / T-MED and whole-chain | Existing component concurrency/revisions/denials rerun, no new real mutation harness | Component/stub evidence retained; REAL PROVIDER NOT EXECUTED |

Actual unknown/PENDING response at a VALID case would fail; no automatic state mapping. NO_SHOW never implies valid visit. Harness does not create records/verify no-write Patient DB, so cannot promote historical concurrency/no-write to real-provider evidence.

## 7. Next actions / verification levels

1. Doctor owner implement DOC-REF-v1 in11, provide isolated actual binary/CA/dedicated credentials/synthetic fixtures/deployment proof; run real B-04 profile.
2. Appointment owner resolve own UUID mapping/status lifecycle and implement APT-REF-v1 + B-02 consumer changes; run actual B-05/CTB-02, no Patient-side cast/fallback.
3. Actual Auth/scope provider evidence/config needed for whole chain. Full Patient/TCP/no-write/current scope/correlation then Gateway/Frontend are separate M5-E/F tasks; M6 prerequisites still outside execution.

Consumer contracts **PROJECT_DECIDED / CODE COMPLETE** (M5-A/B/C preserved). Component **VERIFIED**; historical TCP **VERIFIED WITH STUB**. Doctor/Appointment **PROVIDER BLOCKED/NOT VERIFIED**; B-02 actual consumer **BLOCKED**; whole-chain **INTEGRATION NOT VERIFIED**. M5-D preparation delivered, M5-D overall and M5/release not complete.

Source changes this task: test/harness/POM/docs only. No production code semantics, new bypass/credentials/default grant, shared-module edit, canonical change, migration/legacy DB/write or Git history change.

