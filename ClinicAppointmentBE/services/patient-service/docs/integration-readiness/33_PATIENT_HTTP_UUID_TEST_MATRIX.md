# 33 — Fresh HTTP + UUID test matrix

`mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp`: **BUILD SUCCESS, 287 tests, 0 failures, 0 errors, 0 skipped**. 207 Surefire + 80 Failsafe. All 257 baseline JUnit suite/name pairs retained and PASS with target UUID assertions; 30 additional test cases. No accepted test/helper source removed; no test disabling or production security bypass.

| Suite | Tests | Failures/errors/skipped |
|---|---|---|
| PatientAuthenticationTest | 4 | 0/0/0 |
| PatientProviderContractTest | 3 | 0/0/0 |
| ProviderContractPreflightTest | 3 | 0/0/0 |
| IdentityConfigurationTest | 5 | 0/0/0 |
| IntrospectionDecoderTest | 30 | 0/0/0 |
| IntrospectionTransportTest | 9 | 0/0/0 |
| PatientInputTest | 24 | 0/0/0 |
| PatientOrchestrationTest | 5 | 0/0/0 |
| PatientPolicyTest | 5 | 0/0/0 |
| AppointmentReferenceDecoderTest | 13 | 0/0/0 |
| AppointmentReferenceTransportTest | 4 | 0/0/0 |
| DoctorReferenceDecoderTest | 16 | 0/0/0 |
| DoctorReferenceTransportTest | 8 | 0/0/0 |
| LocalHttpUuidTest | 26 | 0/0/0 |
| ReferenceConfigurationTest | 7 | 0/0/0 |
| ScopeAdapterTest | 9 | 0/0/0 |
| ScopeConfigurationTest | 5 | 0/0/0 |
| ScopeDecisionDecoderTest | 27 | 0/0/0 |
| ScopePolicyTest | 4 | 0/0/0 |
| PatientIdentityTcpIT | 15 | 0/0/0 |
| PatientServiceIT | 22 | 0/0/0 |
| PatientUuidJarIT | 1 | 0/0/0 |
| PatientReferenceTcpIT | 23 | 0/0/0 |
| PatientScopeTcpIT | 19 | 0/0/0 |

| Boundary | Fresh classification |
|---|---|
| HTTP localhost / 127.0.0.1 TCP | VERIFIED with test-owned server and production adapter |
| IPv6 ::1 | URI policy VERIFIED; live IPv6 socket NOT EXECUTED |
| External/LAN/public/0.0.0.0/Docker/encoded paths/userInfo/query/fragment | DENIED before exchange |
| Basic/no anonymous fallback/version/content-type/JSON/UUID/exact resource | VERIFIED with doubles; unchanged strict failure semantics |
| Redirect / outage / timeout / bounds / capacity / no writes | Retained + new component/TCP assertions PASS |
| HTTPS CA/hostname failures, no downgrade | Retained HTTPS Doctor/Appointment transport suites PASS under target version2, doubles |
| PostgreSQL16.10 UUID schema/JPA validate/FK/unique/revision/random UUIDv4 | VERIFIED on fresh isolated native databases |
| No-op/stale409/concurrent update/deactivation/revision rollback | Retained integration assertions PASS |
| USER/SERVICE/ownership/forged headers/denied grants/authority outage/all four tables | Retained security TCP/PG assertions PASS; Auth/Scope doubles |
| Actual packaged Patient JAR, local profile, HTTP reference config | VERIFIED: healthUP; anonymous401; unavailable Auth503; all four tables empty |
| Positive full Patient clinical flow | COMPONENT/TCP WITH DOUBLES and real PG; not real provider E2E |
| Actual Doctor/Appointment UUID providers | OWNER PENDING / NOT EXECUTED |
| Actual Auth/Scope UUID authorities | PENDING |
| Historical HTTPS B-04/B-05 provider evidence | OLD-CONTRACT EVIDENCE; no target upgrade |

New cases: 26 LocalHttpUuidTest invocations, 2 added real-PG/API cases, 1 golden-schema case, 1 actual-JAR smoke. Scope remains Patient only.

Full nine-module `mvn -f ClinicAppointmentBE/pom.xml verify -B -ntp`: **FAIL** at Notification `application/dto/event/AppointmentNotificationEvent.java:19`, missing `AppointmentSnapshotDto`; Patient independently PASS287 in that run. Other-module success only means Maven success observed, not a restored full historical test inventory. No Notification/Appointment workaround. Full run is on the bao-based candidate, not current remote main. Historical534 is not fresh proof.

Fresh final XML/log SHA-256 and exact cases are in machine evidence/private archive. Private raw logs, databases, certificates and binaries are excluded from the patch.
