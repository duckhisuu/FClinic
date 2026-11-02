# 32 — Proposed UUID contracts: owner action required

Status: **PROPOSED / NOT GROUP-APPROVED**. All numeric business-ID v1 evidence is OLD-CONTRACT EVIDENCE. No shared canonical file changed. No numeric-ID fallback or silent mapping.

| Owner | Exact target | Version / payload |
|---|---|---|
| Doctor | GET `http://localhost:8082/api/v1/doctors/{doctorUuid}` | `X-FClinic-Reference-Contract: patient-doctor-reference/2`; full selected Doctor fields, `id` canonical UUIDv4, active boolean |
| Appointment | GET `http://localhost:8083/internal/v1/appointments/{appointmentUuid}/medical-reference` | `patient-appointment-medical-reference/2`; exact id/patientId/doctorId UUIDv4 and status |
| Auth | Existing explicit HTTPS introspection URI; POST form token, dedicated Basic | `contractVersion: patient-identity-introspection/2`; userId/doctorId/patientId UUIDv4 or permitted null; sub/iss/aud/exp/principalType/authorities remain strict |
| WorkScope | Existing explicit HTTPS authority URI; operation-bound POST JSON, dedicated Basic | `patient-work-scope/2`; caller.doctorId and nullable appointmentId UUIDv4; assignmentId/VALID_VISIT visitId UUIDv4; exact full request echo/current assignment evidence |

Reference request: dedicated independent Basic credentials, Accept application/json, exact version header; no anonymous/human bearer fallback. Response: application/json, exactly one matching version marker. 200 envelope status/message/data; 404 only exact selected domain error envelope (`DOCTOR_NOT_FOUND`/`APPOINTMENT_NOT_FOUND`) and no resource data. 401/403/5xx/wrong wire translate to dependency503, never grant access or disclose provider internals. Credential/version denial must be protected before resource disclosure.

Schemas/goldens: `contracts/*v2-proposed.schema.json`, Doctor/Appointment `*v2.golden.json`, proposed API `patient.uuid-proposed.openapi.json`, real-provider vector templates `*v2-proposed.example.json`. Medical API example IDs are fixtures, never authority-approved mappings.

Required owner tests: valid UUID, numeric/v1/variant/noncanonical/wrong resource rejected; machine-auth absent/wrong rejected; missing/wrong version rejected; exact404 and malformed responses; Doctor inactive facts; Appointment actual persisted patient/Doctor ownership and CONFIRMED/CANCELLED/COMPLETED/NO_SHOW facts. CANCELLED/mismatched facts cause422; PENDING/unknown/provider errors cause503, never valid encounter. Patient still requires genuine clinical WorkScope/VALID_VISIT before references and write transaction. Proposed Auth/Scope UUID assignments must preserve issuance/revocation/current assignments/operation scoping; do not infer ownership from email/phone/browser headers.

Current remote incompatibilities: Doctor `api/dto/DoctorResponse.java: Long id`; Appointment `application/result/MedicalReferenceView.java: long id/doctorId`; Appointment controller exposes public `/api/v2/appointments/{long id}/medical-reference`, not selected protected `/internal/v1/...`. Transport/DTO approval and actual binary verification are pending. Do not widen Patient decoders to conceal this. Real-provider checks now require v2 UUID vectors with actual binary provenance; old v1 vectors cannot qualify the target.

Additional incoming-consumer break: Appointment `infrastructure/adapter/HttpAppointmentReferenceAdapter.java` still calls `settings.patientUrl() + "/api/v2/patients/" + patient`. This candidate intentionally exposes proposed `/api/v3/patients/{uuid}` instead. Appointment owner must review that route/version transition and retain SERVICE-only scoped ACTIVE lookup with Auth/Scope UUIDv2 authorities before cutover. Frontend/Gateway consumers also need separate group review of proposed v3 routing. No alias, provider edit or compatibility claim is supplied here. This is independent of the outgoing B-05 UUID response issue.
