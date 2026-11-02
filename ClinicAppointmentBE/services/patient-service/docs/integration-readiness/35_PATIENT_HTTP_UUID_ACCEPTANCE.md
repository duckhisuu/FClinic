# 35 — Patient HTTP + full UUID acceptance

Verified on 01/11/2026 (Asia/Saigon), isolated `feature/patient-http-uuid`, base `a097c67dbbbf399ec02eeb5b26b801946b7a6f41`. This completes the Patient-owned implementation and independent verification; proposed cross-service contracts require owners/group approval before integration. Original working tree untouched.

| Acceptance | Result |
|---|---|
| PATIENT FULL UUID | COMPLETE within Patient; no numeric business entity ID in active Java/JPA target |
| PATIENT HTTP LOCAL | COMPLETE; exact loopback allowlist, no redirect/downgrade/anonymous fallback |
| PATIENT DB UUID | VERIFIED, fresh native PostgreSQL16.10, Hibernate validate, UUID PK/FK/revision/constraints/concurrency/rollback |
| PATIENT REGRESSION | PASS287; retained257 + new30; zero failures/errors/skipped |
| PATIENT LOCAL RUN | VERIFIED actual JAR boot/health/fail-closed; positive operations COMPONENT/TCP WITH DOUBLES |
| HTTPS TRANSPORT REGRESSION | PASS with test-owned TLS providers; real old B-04/B-05 results remain old-contract evidence |
| PATIENT ↔ APPOINTMENT UUID CONTRACT | Mock contract VERIFIED / real provider OWNER PENDING |
| PATIENT ↔ DOCTOR UUID CONTRACT | Mock contract VERIFIED / real provider OWNER PENDING |
| REAL AUTH/SCOPE | PENDING; proposed version2 consumers, existing HTTPS/fail-closed preserved |
| FULL REACTOR | FAIL — Notification owner, missing AppointmentSnapshotDto at AppointmentNotificationEvent.java:19 |
| LEGACY DATA | LEGACY DATA MIGRATION REQUIRES OWNER MAPPING; no existing DB migrated |
| GIT PUSH / MAIN MERGE / PR #3 UPDATE | NOT PERFORMED |

Business IDs: Patient, medical record, both revision PKs and parent FKs, Doctor/Appointment, proposed Auth userId/Doctor mapping and Scope assignment/visit UUIDv4. Subject/serviceName/audit actors stay opaque. Numeric versions/counters/fees/timeouts/epoch remain numeric. New API `/api/v3/patients`, proposed Patient-owned UUID OpenAPI and `/2` consumers intentionally break old numeric contracts; no silent legacy fallback. Original canonical shared contracts are unchanged.

Security order/authorization, ACTIVE guard, VALUE UUID equality/ownership, valid-visit requirements, no unauthorized reads/writes, atomic four-table revision behavior and optimistic conflict semantics are preserved by retained tests. HTTP reference support leaves Auth/Scope TLS unchanged. No provider fake production code introduced. Default reference clients remain disabled. No booking/relay/SMTP settings changed.

Exact patch/allowlist/SHA manifest are archived outside Git. The patch includes 87 Patient-only paths, no deletion, no binary, no unrelated service/shared file. Temporary external index is used only to generate/check patch; no commit and original index/history remain unchanged. Source preconditions and final hashes are in machine evidence; its own hash lives in the external final manifest to avoid recursion. Review with `git apply --check` in a clean isolated bao checkout, never original dirty main. If bao advances, rebase/port in a new reviewed clone and rerun tests; no force update.

Owner handoff: [32](32_PATIENT_UUID_PROVIDER_HANDOFF.md). Demo/patch instructions: [34](34_PATIENT_HTTP_UUID_DEMO_RUNBOOK.md). Test matrix: [33](33_PATIENT_HTTP_UUID_TEST_MATRIX.md). DB migration: [30](30_PATIENT_FULL_UUID_MIGRATION.md). Whole FClinic readiness is not claimed.

## Exact Patient-only delta

| Action | Path |
|---|---|
| MODIFY | `ClinicAppointmentBE/services/patient-service/README.md` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/29_PATIENT_HTTP_UUID_IMPACT_AUDIT.md` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/30_PATIENT_FULL_UUID_MIGRATION.md` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/31_PATIENT_HTTP_LOCAL_IMPLEMENTATION.md` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/32_PATIENT_UUID_PROVIDER_HANDOFF.md` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/33_PATIENT_HTTP_UUID_TEST_MATRIX.md` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/34_PATIENT_HTTP_UUID_DEMO_RUNBOOK.md` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/35_PATIENT_HTTP_UUID_ACCEPTANCE.md` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/contracts/patient-appointment-medical-reference-v2-proposed.schema.json` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/contracts/patient-appointment-reference-v2.golden.json` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/contracts/patient-doctor-reference-v2-proposed.schema.json` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/contracts/patient-doctor-reference-v2.golden.json` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/contracts/patient-identity-introspection-v2-proposed.schema.json` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/contracts/patient-work-scope-v2-proposed.schema.json` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/contracts/patient.uuid-proposed.openapi.json` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/contracts/real-appointment-vectors-v2-proposed.example.json` |
| ADD | `ClinicAppointmentBE/services/patient-service/docs/integration-readiness/contracts/real-doctor-vectors-v2-proposed.example.json` |
| ADD | `ClinicAppointmentBE/services/patient-service/evidence/patient-http-uuid-id-inventory.json` |
| ADD | `ClinicAppointmentBE/services/patient-service/evidence/patient-http-uuid-verification.json` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/api/controller/PatientController.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/api/dto/MedicalRecordResponse.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/api/dto/PatientInput.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/application/port/out/AppointmentClientPort.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/application/port/out/DoctorClientPort.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/application/port/out/PatientRepositoryPort.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/application/port/out/WorkScopePort.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/application/security/PatientPolicy.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/application/security/TrustedCaller.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/application/usecase/PatientApplicationService.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/application/usecase/PatientWriter.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/config/PatientConfiguration.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/config/PatientWebConfiguration.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/domain/aggregate/MedicalRecord.java` |
| ADD | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/domain/model/BusinessId.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/domain/model/SearchQuery.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/JpaPatientRepositoryAdapter.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/identity/IntrospectionDecoder.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/AppointmentReferenceDecoder.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/DoctorReferenceDecoder.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/ReferenceHttpClient.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/ReferenceJson.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/ReferenceSettings.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/RemoteAppointmentClientAdapter.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/reference/RemoteDoctorClientAdapter.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/scope/RemoteWorkScopeAdapter.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/scope/ScopeDecisionDecoder.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/adapter/scope/ScopeQuery.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/entity/JpaMedicalRecordEntity.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/entity/JpaMedicalRevisionEntity.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/entity/JpaPatientEntity.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/infrastructure/entity/JpaPatientRevisionEntity.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/resources/application-appointment-reference.yml` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/resources/application-doctor-reference.yml` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/resources/application-identity-introspection.yml` |
| ADD | `ClinicAppointmentBE/services/patient-service/src/main/resources/application-local.yml` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/resources/application-scope-authority.yml` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/main/resources/application.yml` |
| ADD | `ClinicAppointmentBE/services/patient-service/src/main/resources/db/schema/uuid/V001__patient_uuid_empty.sql` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/PatientInputTest.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/PatientOrchestrationTest.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/PatientPolicyTest.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/PatientServiceIT.java` |
| ADD | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/PatientUuidJarIT.java` |
| ADD | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/UuidFixtures.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/config/PatientAuthenticationTest.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/contract/ContractSchemas.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/contract/PatientProviderContractTest.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/contract/ProviderContractPlan.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/contract/ProviderProbe.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/contract/RealProviderContractCheck.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/identity/IdentityFixtures.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/identity/IntrospectionDecoderTest.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/identity/PatientIdentityTcpIT.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/reference/AppointmentReferenceDecoderTest.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/reference/AppointmentReferenceTransportTest.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/reference/DoctorReferenceDecoderTest.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/reference/DoctorReferenceTransportTest.java` |
| ADD | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/reference/LocalHttpUuidTest.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/reference/PatientReferenceTcpIT.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/reference/ReferenceConfigurationTest.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/reference/ReferenceFixtures.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/reference/TestReferenceServer.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/scope/PatientScopeTcpIT.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/scope/ScopeAdapterTest.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/scope/ScopeFixtures.java` |
| MODIFY | `ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/scope/ScopePolicyTest.java` |
| ADD | `ClinicAppointmentBE/services/patient-service/src/test/resources/contracts/patient.uuid-proposed.openapi.json` |
