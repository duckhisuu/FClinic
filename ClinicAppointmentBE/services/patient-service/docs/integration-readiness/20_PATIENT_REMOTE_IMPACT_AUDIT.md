# 20 — Patient remote impact audit R3

2026-10-29T09:38:47.133102+07:00. **PATIENT SOURCE CONFLICTS: NONE. PATIENT PRODUCTION CHANGES: NONE.** Original `main@a6bf54e0edc6fa513e07b7e91a5ddaeefd1a58c9`; index `73c602f75f6ab8b81d0779964dd6f2ac8c3f680d9c9a72cfe8e6d3386ee9d184` unchanged. Existing Appointment test-only repair/untracked work retained. Clone was read-only: HEAD/index/MERGE_HEAD, all 1,501 source paths and 15 Appointment conflict stages unchanged. No merge/pull/commit/push or source restore performed.

Fresh GitHub heads were checked and refs fetched without checkout/source/index mutation. origin/main and origin/appoiment remain b6554cb2dce6a9277b5beccb3a7ce275330d679f; origin/nam=61ec5e74e9d11286b41884ed25f033e15ce2485e; bao=6a55548; datdt=3984842. Exact refs/commits and branch diffs are in machine evidence/private logs `D:\FClinic_SafeSync\p3-1029T022851-a1ac83`.

## Answers to P1

1. Appointment commit b6554cb directly changes no Patient Java production code; only application.yml gains optional .env imports.
2. It deletes exactly **33 Patient test/helper files**, listed below. All remain locally and all 33 match accepted P-FINAL SHA-256.
3. Current main/appoiment/nam/bao Patient Java API and SQL schema trees match original HEAD. No selected API/schema change is present in original. Old datdt is a pre-Patient baseline branch, not a newly selected Patient update.
4. Patient POM/dependencies match on main/appoiment/nam/bao. b6554cb adds .env imports; newer nam also changes datasource defaults. Neither config was adopted; original retains explicit PATIENT_V2_DB_* with validate-only/no initialization. No shared Maven/config change was applied.
5. Incoming deletions/config changes are absent from original. Raw tree differences also show P-FINAL docs/evidence absent from remote; they remain intact locally.
6. No Patient unmerged stages exist in original or isolated clone. The 15 Appointment conflicts are outside this task.
7. Group merge must preserve all Patient source/tests/helpers, explicit configuration, P-FINAL docs/evidence, canonical identity/scope/B-04/B-05 pins and current consumer/security semantics. No mass restoration is needed.

All **91 source/POM per-file pins** match P-FINAL, including 33 tests/helpers. Historical test aggregate reproduced from its exact manifest order: `e8b3b9ea40f20f510433e82a3ab3ec8eeaea15f352ce456a34af4991e3ed667e`. Seven canonical contract pins match. Initial audit used ordinal sorting for the aggregate; historical ordered records use case-insensitive order. Per-file hashes always matched; first audit attempt retained separately, no source fix involved.

## Exact remote-deletion inventory

| Patient-relative source | Original SHA-256 (= P-FINAL) | Versus remote parent |
|---|---|---|
| `src/test/java/com/fclinic/patientservice/PatientInputTest.java` | `edc0f0f896a7caac15450d2bde6259d68f2c728996b1dc70aa3581e6b0a619f2` | byte-identical |
| `src/test/java/com/fclinic/patientservice/PatientOrchestrationTest.java` | `e740c7edd797c1cee4c695c973f368e038c2a68828e665ac8da4566642d8aead` | byte-identical |
| `src/test/java/com/fclinic/patientservice/PatientPolicyTest.java` | `6bc6331004da7a034b6c18234459a1bf6f99c0487acb29d4735540ad1eb315f9` | byte-identical |
| `src/test/java/com/fclinic/patientservice/PatientServiceIT.java` | `12efc9fa51cb1fc9d6426b20bd5b895e0f453e8007214c7a2c59924b1dd1423f` | line endings only |
| `src/test/java/com/fclinic/patientservice/config/PatientAuthenticationTest.java` | `c11c8358c6f1b0a6f674486a27954d4306d28a5d85ec18d112d56cd2e5fea9d5` | byte-identical |
| `src/test/java/com/fclinic/patientservice/contract/ContractSchemas.java` | `98d5fc58ee8381a64e5336e2a4ef8c7c79b7fee45d3fb1b8977e5c9eb6a6525a` | byte-identical |
| `src/test/java/com/fclinic/patientservice/contract/PatientProviderContractTest.java` | `7ca54263921f3b82290c242403271ff4ede41fc30d41694052de1cff9f1ecd42` | byte-identical |
| `src/test/java/com/fclinic/patientservice/contract/ProviderContractPlan.java` | `41da207020aebf6a5e718e5c5bb96974de98608a4cd22874f23799c255076983` | byte-identical |
| `src/test/java/com/fclinic/patientservice/contract/ProviderContractPreflightTest.java` | `31654cc744170308a237ba68dff3b77a879e9b99ff0900262a5bea5149e23acc` | byte-identical |
| `src/test/java/com/fclinic/patientservice/contract/ProviderProbe.java` | `2de2ae1c1a30ec5752b025bc6fc347e1d70b35bf8e2949fa050e1b563dbe8ab7` | byte-identical |
| `src/test/java/com/fclinic/patientservice/contract/RealProviderContractCheck.java` | `e3fa1604864e7c2de78ab1dd2c8bc55a06e09d6cb6f8573f5df6e4da3256c0be` | byte-identical |
| `src/test/java/com/fclinic/patientservice/identity/IdentityConfigurationTest.java` | `accb845c79aca7fe32985213799618a2c2bf78223d1701dcb9895cbf7c4889fb` | byte-identical |
| `src/test/java/com/fclinic/patientservice/identity/IdentityFixtures.java` | `3fe13319c8eee9aed55fcb7e66b5caed21df370a11fc879f4725a92728a54208` | byte-identical |
| `src/test/java/com/fclinic/patientservice/identity/IntrospectionDecoderTest.java` | `c2e8699e1b5305e19ade177ccea72da1b87a9820eb110a26ab07fd057f40d99d` | byte-identical |
| `src/test/java/com/fclinic/patientservice/identity/IntrospectionTransportTest.java` | `a61b012f3fd6759c7cdbc4488e038a251127bc017fbf5957659817eac6ae4d34` | byte-identical |
| `src/test/java/com/fclinic/patientservice/identity/PatientIdentityTcpIT.java` | `57c275a670e385ffffc09fe8d25d928e0283a9cbd54ce5d146b028a63d008df6` | accepted P-FINAL security extension retained |
| `src/test/java/com/fclinic/patientservice/identity/RealAuthIdentityContractCheck.java` | `627f90ced3b9dd796608da2b11ad2a7d05d2d6a5a24c2df60d8feda5864ba886` | byte-identical |
| `src/test/java/com/fclinic/patientservice/identity/TestAuthServer.java` | `502ec7484210bbc86373c6a41f9fe3a94439fb9c176784a270a2b216e2caae61` | byte-identical |
| `src/test/java/com/fclinic/patientservice/reference/AppointmentReferenceDecoderTest.java` | `d6574c55616fb069e3255bb4097dc6d8a3f5eb45d18b4cd69c2e6f7008cd37bf` | byte-identical |
| `src/test/java/com/fclinic/patientservice/reference/AppointmentReferenceTransportTest.java` | `105c940b2a357680edb80a0d86e005a3ceacab94cdfc1e0597b5c578ca89528b` | byte-identical |
| `src/test/java/com/fclinic/patientservice/reference/DoctorReferenceDecoderTest.java` | `d6179d9fe1b1ac99fe20cfe68f80c59e912d6082b5280f134eaf85e03bf07d8f` | byte-identical |
| `src/test/java/com/fclinic/patientservice/reference/DoctorReferenceTransportTest.java` | `216db3ab091749e13beb2d319a08ee46ebf1dda336eb4c8518572afa564f5815` | byte-identical |
| `src/test/java/com/fclinic/patientservice/reference/PatientReferenceTcpIT.java` | `bf9cada6c0bbdb9f4712f466932e7dae9396236aba2a3fca1b03b3218c046006` | byte-identical |
| `src/test/java/com/fclinic/patientservice/reference/ReferenceConfigurationTest.java` | `974d571f9037571b3bfc97963a0e251b0131594f42d3051fcc1b953b5d154a3c` | byte-identical |
| `src/test/java/com/fclinic/patientservice/reference/ReferenceFixtures.java` | `c6f30a09982feeef03322b6532db344af4b9fdd4fb4716f72409614d8c764dc7` | byte-identical |
| `src/test/java/com/fclinic/patientservice/reference/TestReferenceServer.java` | `5a272196bf2bf9c9b8ea42daa420f97c37963685d1b78f9556c17de47bd6faf7` | byte-identical |
| `src/test/java/com/fclinic/patientservice/scope/PatientScopeTcpIT.java` | `bc1105e06837f17af754ed9b46eb42fc069629066cbc3c72a1ef8983f6425821` | byte-identical |
| `src/test/java/com/fclinic/patientservice/scope/ScopeAdapterTest.java` | `b14a0a658a23950bc3e55c24766e8cbc633764bb524e21abf143d98e63e74184` | byte-identical |
| `src/test/java/com/fclinic/patientservice/scope/ScopeConfigurationTest.java` | `5ed50048ffab6e5dbc535457989885ba3dbea8763904fbb39eed151a021eba7e` | byte-identical |
| `src/test/java/com/fclinic/patientservice/scope/ScopeDecisionDecoderTest.java` | `64fbe92f5e178746e0f53d41c6cff49f0b4a1d0a79f6ae0ddcdcb4eda96a03fc` | byte-identical |
| `src/test/java/com/fclinic/patientservice/scope/ScopeFixtures.java` | `66e21128ee654a3130b8970974805b8769a1ed25cb4bd74d0d589cf184af64f0` | byte-identical |
| `src/test/java/com/fclinic/patientservice/scope/ScopePolicyTest.java` | `63daa09b1c46c7c049d3105bec9f15c2020771ef65f3238e8045ca5244f60257` | byte-identical |
| `src/test/java/com/fclinic/patientservice/scope/TestScopeServer.java` | `1434ca9f45e51de1d8c7b6d2ea2bebd50f0d92adafa2aba991530b61a728d185` | byte-identical |

31 files are byte-identical to the remote parent; one differs only by line endings; PatientIdentityTcpIT retains three additional P-FINAL regressions for invalid Auth contract/no writes, inactive introspection blocking no-op/changed PUTs, and Auth deadline preventing administrative/clinical writes across all four tables. Do not replace it with the older remote-parent copy.
