# 29 — HTTP + full UUID impact audit

Implementation baseline: `bao@a097c67dbbbf399ec02eeb5b26b801946b7a6f41`. Isolated branch `feature/patient-http-uuid`. Remote main observed `3f833711e282e06b71cc88f7030ce762d78ec201`; no main code imported. Original `main@a6bf54e0edc6fa513e07b7e91a5ddaeefd1a58c9` and index preserved. Backup/SHA inventory was taken before edits; original Patient snapshot and full original path hashes are outside repository.

| Boundary | Before | Target |
|---|---|---|
| Patient PK / all patient FKs | UUID | canonical lowercase UUIDv4, native PostgreSQL uuid |
| Medical record PK, nested route/response | Long / IDENTITY | application-generated UUIDv4 |
| Doctor ID in clinical record/caller/reference | long/Long | UUIDv4 |
| Appointment ID/filter/reference | nullable Long | nullable UUIDv4 |
| Patient/medical revision PK | Long / IDENTITY | Hibernate random UUIDv4 |
| Revision parent FK | UUID Patient / Long record | UUID |
| Auth userId/doctorId | positive safe integer | proposed `/2` UUIDv4; no sub-to-ID inference |
| Scope appointmentId/caller.doctorId | numeric | proposed `/2` UUIDv4 |
| Scope assignmentId/visitId evidence | opaque entity strings | proposed `/2` UUIDv4; authority mapping required |
| legacy_patient_id | nullable Long staging hint | absent in fresh UUID schema/JPA; old schema remains historical |
| version/entityVersion, pagination, epoch expiry, money/limits/status | numeric | unchanged numeric |
| subject, serviceName, actor_id/createdBy/updatedBy | opaque trusted audit strings | unchanged; not treated as entity keys |

Full pre-edit file/line inventory (including numeric exceptions): `../../evidence/patient-http-uuid-id-inventory.json`. All 33 accepted test/helper sources and retained test method inventory are preserved and migrated, never deleted. Historical P-FINAL/R3, v1 schemas, old V001 and old provider evidence remain unchanged; they describe the old implementation, not target verification.

Current-main static evidence: Doctor `api/dto/DoctorResponse.java` still `Long id`; Appointment `application/result/MedicalReferenceView.java` still `long id` and `long doctorId`, public `/api/v2/appointments/{id}/medical-reference`. These fail the proposed UUID contract and protected B-05 target. No compatibility fallback/provider edit performed. Actual target providers OWNER PENDING.

Only Patient production/tests/config/schema/docs change. Shared canonical OpenAPI, fixtures registry, Auth/Scope production, other services and Appointment merge clone untouched. Patient v3 OpenAPI is a Patient-owned proposal; group/provider approval remains required before integration.

Incoming Appointment lookup currently calls `/api/v2/patients/{uuid}` in `HttpAppointmentReferenceAdapter.java`; proposed Patient `/api/v3` is an additional API-routing breaking change. Exact owner action is in report32.
