# 37 — Group decision and owner handoff

All new UUID contracts remain **PROPOSED**; no group approval or real clinical E2E claimed. Basic reference auth remains distinct from human/identity/scope credentials. Request ownership/VALID_VISIT/current scope checks may not be bypassed for demo.

| Contract | Current implementation | Proposed target / breaking change | Owner | Approval | Runtime status | Blocking action |
|---|---|---|---|---|---|---|
| Doctor reference | DoctorResponse Long id | GET /api/v1/doctors/{UUIDv4}; patient-doctor-reference/2; full selected Doctor data/active; localhost HTTP + dedicated Basic/version/error envelope | Doctor | PROPOSED / PENDING | Patient double TCP verified; actual UUID provider NOT VERIFIED | Confirm UUID migration, protected endpoint, exact404/auth/version/malformed/UUID negative tests |
| Appointment reference | MedicalReferenceView long id/doctorId; public numeric route | GET /internal/v1/appointments/{UUIDv4}/medical-reference; patient-appointment-medical-reference/2; exact id/patientId/doctorId/status | Appointment | PROPOSED / PENDING | Double TCP verified; actual provider NOT VERIFIED | UUID persisted facts, machine auth/version, ownership/status/PENDING/no-disclosure tests |
| Incoming Patient lookup | Appointment HttpAppointmentReferenceAdapter calls /api/v2/patients/{uuid} | /api/v3/patients/{uuid}; retain real scoped SERVICE and ACTIVE semantics | Appointment + Patient/group routing owners | PROPOSED / PENDING | Actual cross-service NOT VERIFIED | Approve API transition then change Appointment consumer separately; no silent v2 alias |
| Identity introspection | No verified actual authority; Patient HTTPS-only | patient-identity-introspection/2, UUID userId/doctorId/patientId, opaque sub unchanged | Auth | PROPOSED / PENDING | COMPONENT/TCP WITH DOUBLES only | Confirm issuance/linking/active/revocation/USER-vs-SERVICE/issuer/audience/expiry; no fake tokens |
| Administrative/clinical scope | No verified actual authority; Patient HTTPS-only | patient-work-scope/2, Doctor/appointment/assignment/visit UUIDv4; exact operation echo/current grants | WorkScope | PROPOSED / PENDING | COMPONENT/TCP WITH DOUBLES only | Confirm authority-owned mapping/current assignment/revocation/VALID_VISIT; no encounter bypass |
| Notification build | AppointmentNotificationEvent.java:19 cannot find AppointmentSnapshotDto | Suspected missing import com.fclinic.notificationservice.application.dto.AppointmentSnapshotDto | Notification | OWNER ACTION REQUIRED | Full reactor compile FAIL | Owner adds/reviews import and reruns build; this does not verify event integration |
| Backend CI coverage | compile + test only | Add owner-approved verify/Failsafe coverage, keep required checks | Repository CI maintainer | PENDING | Full build blocked; module verify separate | Do not accept green unit-only CI as complete regression |

Doctor target full selected fields, exact schemas, golden fixtures, negative cases and B-05 statuses are in report32/contracts/*v2-proposed*. Preserve CANCELLED→422 mismatch; unknown/PENDING/provider failures→503; persisted Patient/Doctor ownership must match. Old numeric-ID B-04/B-05 evidence remains OLD-CONTRACT EVIDENCE.

Auth/Scope transport mismatch: enabling Patient local reference profile does not enable HTTP introspection or scope. Their current settings reject all HTTP, including localhost. No actual HTTP-only Auth/Scope provider/config/provenance was demonstrated here; positive real-provider status stays PENDING. A team HTTP-only demo therefore cannot run the complete authenticated clinical chain under current policies. Owners may supply valid local HTTPS authorities, or approve a separate Patient-owned local-only HTTP transport proposal with explicit opt-in + active local profile + exact loopback allowlist, unchanged dedicated credentials/wire validation/authorization, no proxy/redirect/downgrade and corresponding denial/no-write regression. That proposal is documentation only and is NOT implemented in this patch.

Notification suspected minimum owner fix (not applied here):

```java
import com.fclinic.notificationservice.application.dto.AppointmentSnapshotDto;
```

No source or approval decision is made for other services. No external message was sent to owners; this document and Draft PR provide the handoff.
