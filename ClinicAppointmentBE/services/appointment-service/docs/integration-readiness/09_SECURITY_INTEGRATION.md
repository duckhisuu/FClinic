# Appointment / Auth / Patient security integration — 09/11/2026

This handoff supersedes the older Appointment statement that security was excluded. No database migration or reset of an existing realm/volume is required by this change.

## Request behavior

- Every Appointment v2 HTTP operation authenticates at the service itself. Browser identity headers and a missing Principal cannot create an anonymous actor. Missing/malformed/inactive/expired bearer: 401; authenticated caller outside policy: 403; failed Auth/authority transport or malformed upstream contract: 503.
- Original USER bearer is introspected by Auth using a separate Appointment introspection client. Auth verifies current activation and returns the current account/business mapping for the verified Keycloak subject. No username fallback or invented audience is used by introspection. The caller's bearer never becomes the provider credential.
- PATIENT creates, reads, cancels and reschedules only their linked patientId. Unlinked accounts gain no object access. DOCTOR reads only bookings for their doctorId. ADMIN has no implicit Appointment access. STAFF actions require explicit current assignments from Auth.
- All mutation authorization precedes idempotency lookup/replay, including replay with an old version. Cancel/reschedule authorization uses the appointment's stored immutable patientId. Audit records the verified subject and PATIENT/STAFF role.
- List filters are constrained by self patientId, assigned doctorId, or the finite STAFF patient set before database pagination/counting. Empty assigned sets produce an empty page; no assignment returns 403.
- Patient's Basic client credential is accepted only for the medical-reference projection, with `SVC_APPOINTMENT_READ` and serviceName patient-service. It cannot read full appointments/history or mutate. A separate Notification credential applies only to reminder-reference. The reference `/2` header remains on medical-reference 200/404/error responses.

## Provisioning and configuration

Use the backend `.env.example` as a checklist; populate secrets externally. Do not reuse introspection, scope, reference and token-client secrets. Empty credentials never enable permissive access. Existing real `.env` files are intentionally not changed.

| Boundary | Configuration |
| --- | --- |
| Appointment → Auth introspection | APPOINTMENT_AUTH_INTROSPECTION_URI / CLIENT_ID / CLIENT_SECRET; the same ID/secret is registered in Auth app.introspection.clients.appointment |
| Appointment → Auth scope | APPOINTMENT_SCOPE_URI / CLIENT_ID / CLIENT_SECRET; Auth client is limited to appointment-work-scope/1 + ADMINISTRATIVE |
| Appointment → Keycloak machine token | APPOINTMENT_PATIENT_TOKEN_URI / CLIENT_ID / CLIENT_SECRET; grant_type client_credentials; access token refreshes before expiration |
| Appointment → Patient | APPOINTMENT_PATIENT_URL; GET /api/v3/patients/{id} with the separate machine token |
| Patient → Auth introspection | PATIENT_AUTH_INTROSPECTION_URI / ISSUER / CLIENT_ID / CLIENT_SECRET; credentials match AUTH_INTROSPECTION_CLIENT_ID/SECRET at Auth |
| Patient → Auth administrative scope | PATIENT_ADMIN_SCOPE_URI / CLIENT_ID / CLIENT_SECRET |
| Patient → Auth clinical scope | PATIENT_CLINICAL_SCOPE_URI / CLIENT_ID / CLIENT_SECRET; distinct from administrative and identity |
| Patient → Appointment | PATIENT_APPOINTMENT_REFERENCE_URI=http://localhost:8083/api/v2/appointments; PATIENT_APPOINTMENT_REFERENCE_CLIENT_ID/SECRET on both sides |
| Machine subject registry | APPOINTMENT_SERVICE_SUBJECT at both Auth and Patient; use the actual Keycloak service-account user UUID |

Appointment can use a supplied APPOINTMENT_SERVICE_TOKEN when no patient-token-uri is configured, preserving explicitly provisioned installations. Prefer client_credentials for refreshable Patient access. It never forwards the human token. Existing Doctor/recipient credentials remain outside this change.

The Keycloak realm template now assigns a stable synthetic service-account UUID `22222222-2222-4222-8222-222222222205`, grants **SVC_PATIENT_READ**, and adds the appointment-service audience mapper. Fresh realm imports receive these values. For an existing realm, update the role mapping/audience mapper through its normal administration and set APPOINTMENT_SERVICE_SUBJECT to its actual service-account subject; reimporting a template does not migrate existing realm data. Obtain a new access token after changing roles/mappers. Do not delete/recreate volumes.

Start Patient with `--spring.profiles.active=service-integration,local` (or equivalent protected environment configuration). The group enables identity-introspection, scope-authority, appointment-reference and auth-registry. The old default-disabled behavior remains for installations that have not configured integration. Both Auth scope endpoints may use `/api/v1/auth/scopes`; credentials bind each client to the appropriate authority. Doctor reference remains explicitly enabled/configured separately when clinical-create requires it.

Patient identity/scope adapters continue to require **HTTPS** and verified certificates. Serve Auth with Spring SERVER_SSL_* configuration or a trusted TLS proxy and set the URIs accordingly. Configure Patient's optional `patient.identity.trust-store`, `patient.scope.administrative.trust-store`, `patient.scope.clinical.trust-store` and corresponding passwords in protected external YAML when using a private CA. Appointment uses the JVM trust anchors; configure the JVM trust store for that CA as needed. No trust-all or hostname-verification bypass is introduced. Appointment Auth/token transports and Patient reference transport permit plain HTTP only on exact loopback for local use; Docker service hostnames require HTTPS with appropriate DNS/certificates.

For Gateway `lb://` routes, enable Eureka registration on the services with EUREKA_ENABLED=true. Gateway now forwards both Appointment v2 and the retired v1 route; v1 still returns 410 at Appointment. It forwards the original bearer, and direct-port access has the same Appointment authorization checks.

## Assignment provider

Auth now exposes a protected POST `/api/v1/auth/scopes`. It implements Patient's `patient-work-scope/2` response shape and Appointment's `appointment-work-scope/1`, echoing the nonce, operation, caller and resource with current assignment evidence. Client credentials are distinct and bound to contract/authority. USER assertions are checked against active Auth users by exact Keycloak subject and current mappings. SERVICE assertions require an exact configured registry binding. Role alone never creates an assignment.

Assignments are owned by protected Auth deployment configuration through ScopeAssignmentPort/ConfiguredScopeAssignments. Each has an explicit UUID, subject, operations, patient set, active flag and validity interval. Current account disable is checked on every request. Assignment edits/revocations require reloading/restarting Auth; no runtime assignment-management API is introduced. Use one grant per subject/operation, placing its finite patient set in that grant. Clinical VALID_VISIT additionally requires an independent UUID visitId, exact visitDate and matching optional appointmentId (null for an explicitly assigned walk-in visit). No human wildcard is accepted.

The only default grant allows the registered appointment-service subject to perform APPOINTMENT_PROFILE_READ. It cannot read Patient medical records or search profiles. Original human authorization has already occurred at Appointment before that credential is used. STAFF and DOCTOR grants are empty by default.

Example protected external Auth configuration for a receptionist, with an explicitly approved patient set:

```yaml
app:
  authorization:
    grants:
      "44444444-4444-4444-8444-444444444402":
        subject: "22222222-2222-4222-8222-222222222202" # verified staff Keycloak subject
        operations:
          - APPOINTMENT_CREATE
          - APPOINTMENT_READ
          - APPOINTMENT_CANCEL
          - APPOINTMENT_RESCHEDULE
          - APPOINTMENT_SEARCH
          - APPOINTMENT_AVAILABILITY
        patient-ids: ["74fba630-5953-44b8-86d6-bc236ac1213b"]
        all-patients: false
        active: true
        valid-from: "2026-11-09T00:00:00Z"
        valid-until: "2026-12-01T00:00:00Z"
```

Patient administrative intake/profile permissions and clinical permissions are separate operation names; they are not implied by APPOINTMENT_* permissions. Empty or expired grants deny. Never add all-patients to human grants to bypass scope errors.

## Verification and remaining dependencies

Run from the repository root:

```powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/auth-service,services/patient-service,services/appointment-service,infra/api-gateway -am verify
./ClinicAppointmentBE/security-checks/Run-Contracts.ps1
```

The second command uses JDK21/JAVA_HOME and the three modules' compiled classes and Surefire dependency classpaths. It checks actual Auth producer output against Patient/Appointment production decoders, current activation, scope evidence and the medical-reference projection. Storage and external Keycloak ports are mocks in those contract checks.

Appointment security tests also exercise the actual Spring application over TCP with Flyway and isolated native PostgreSQL, including booking/cancel/replay, denied ownership and revoked STAFF scope. Upstream identity/Doctor/Patient providers there are doubles. Gateway's test starts its real route stack with a synthetic backend. These tests are not evidence that a real realm/TLS deployment, Doctor UUID provider or Notification email flow is deployed.

The older audit remains a historical snapshot. Its separate Google-login, public account-linking and OTP findings are not resolved by this scope of work. Doctor UUID/published-slot implementation, frontend Patient v3 binding and Notification schema3/recipient delivery are also separate work items.
