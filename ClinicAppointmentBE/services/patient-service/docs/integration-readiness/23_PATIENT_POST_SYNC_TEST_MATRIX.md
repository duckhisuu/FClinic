# 23 — Patient post-sync test matrix R3

**Patient default 257 PASS; full original reactor 534 PASS.** Zero failures/errors/skipped. Source/tests/assertions remain byte-identical to P-FINAL; no tests disabled or replaced. Run logs/XML hashes and all testcase names/statuses are in machine evidence. Reports were archived before the next run and selected using actually logged suites, excluding stale opt-in XML.

Commands: `mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp`; then `mvn -f ClinicAppointmentBE/pom.xml verify -B -ntp`. Full default includes existing provider tests; no separate extra Doctor/B-04 run, remote build/launch or real broker profile was added.

| Patient suite | Cases | Classification |
|---|---:|---|
| config.PatientAuthenticationTest | 4 | Unit/contract/transport component |
| contract.PatientProviderContractTest | 2 | Unit/contract/transport component |
| contract.ProviderContractPreflightTest | 3 | Unit/contract/transport component |
| identity.IdentityConfigurationTest | 5 | Unit/contract/transport component |
| identity.IntrospectionDecoderTest | 30 | Unit/contract/transport component |
| identity.IntrospectionTransportTest | 9 | Unit/contract/transport component |
| PatientInputTest | 24 | Unit/contract/transport component |
| PatientOrchestrationTest | 5 | Unit/contract/transport component |
| PatientPolicyTest | 5 | Unit/contract/transport component |
| reference.AppointmentReferenceDecoderTest | 13 | Unit/contract/transport component |
| reference.AppointmentReferenceTransportTest | 4 | Unit/contract/transport component |
| reference.DoctorReferenceDecoderTest | 16 | Unit/contract/transport component |
| reference.DoctorReferenceTransportTest | 8 | Unit/contract/transport component |
| reference.ReferenceConfigurationTest | 7 | Unit/contract/transport component |
| scope.ScopeAdapterTest | 9 | Unit/contract/transport component |
| scope.ScopeConfigurationTest | 5 | Unit/contract/transport component |
| scope.ScopeDecisionDecoderTest | 27 | Unit/contract/transport component |
| scope.ScopePolicyTest | 4 | Unit/contract/transport component |
| identity.PatientIdentityTcpIT | 15 | COMPONENT/TCP WITH DOUBLES; native isolated PG |
| PatientServiceIT | 20 | Core PG/MockMvc with doubles |
| reference.PatientReferenceTcpIT | 23 | COMPONENT/TCP WITH DOUBLES; native isolated PG |
| scope.PatientScopeTcpIT | 19 | COMPONENT/TCP WITH DOUBLES; native isolated PG |

180 Surefire +77 Failsafe. The 77 comprise PatientServiceIT20, identity TCP15, scope TCP19, reference TCP23. Accepted real B-05 is additional physical provider evidence within the full reactor, not a replacement for component classifications.

| Security invariant | Existing fresh assertions |
|---|---|
| USER self / UUID ownership / forged headers | PatientAuthenticationTest, PatientPolicyTest, PatientIdentityTcpIT, PatientServiceIT; headers cannot grant identity or cross-patient access |
| SERVICE != USER | PatientPolicy/identity/scope cases; only named appointment-service + current grant can read authorized profile; no clinical impersonation |
| ACTIVE/INACTIVE | PatientServiceIT and writer locking: inactive rejects new medical create, preserves defined existing-read/edit semantics |
| Doctor/Appointment relationship and nested record ownership | PatientOrchestrationTest, PatientReferenceTcpIT23, PatientServiceIT; missing/inactive/cancelled/mismatch422 versus malformed/PENDING/outage503; immutable author+Doctor checks |
| Auth outage/invalid/expired/inactive/revoked semantics | Introspection tests + identity TCP; dependency503 versus identity401, before writer/reference calls; lifecycle modeled by doubles only |
| Scope deny/outage/current grant | ScopePolicy/adapter/decoder + scope TCP19; deny403, technical503, no role fallback; re-evaluate no-op/changed writes |
| All four tables unchanged on unauthorized writes | Identity contentSnapshot and scope/reference snapshots inspect patients, medical_records, patient_revisions, medical_record_revisions; P-FINAL invalid-contract/inactive/deadline cases retained |
| Transaction/version/concurrency/revision integrity | PatientServiceIT20 includes rollback, competing PUT, duplicate linked create, deactivation race, no-op stale version and audit revisions |

REAL AUTH/SCOPE: PENDING. No genuine provider, signature/issuance or live revocation verification was performed. Existing Auth/Scope handoffs unchanged. Actual B-05 provider: VERIFIED accepted original only; remote: NOT VERIFIED. B-04 happened as an existing full-reactor regression suite, not a new Doctor implementation task. No shared DB/schema migration/booking/default relay/SMTP/delivery activation.
