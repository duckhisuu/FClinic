# 34 — PowerShell local UUID/HTTP demo

Use the isolated `feature/patient-http-uuid` checkout. No automatic Git commit/push. This is a breaking proposed API/consumer contract; do not point at an existing DB. No TLS certificate/truststore is needed for Doctor/Appointment HTTP loopback. Auth/Scope are unchanged HTTPS consumers and remain pending.

## Repeat the verified synthetic tests

```powershell
mvn -f ClinicAppointmentBE/pom.xml -pl services/patient-service -am verify -B -ntp
```

This runs fresh test-owned native PG, real Patient TCP with Auth/Scope doubles, HTTP reference doubles, TLS negative/regression tests, and actual Patient JAR startup/fail-closed smoke. Doubles are test-only and cannot be enabled as production authorities. Runtime logs/databases remain ignored under target; do not add them to Git.

## Prepare a fresh disposable PostgreSQL database

Use a PostgreSQL instance that you own exclusively for this demo, bound to 127.0.0.1, with a disposable bootstrap account. Set $PgPort/$PgUser from that instance; do not reuse shared/existing DB content. Supply its disposable password via interactive psql prompt (`-W`).

```powershell
$PgPort = [int](Read-Host 'Port of your isolated PostgreSQL instance')
$PgUser = Read-Host 'Disposable PostgreSQL bootstrap user'
$DemoDb = 'patient_uuid_demo_' + [guid]::NewGuid().ToString('N')
createdb -h 127.0.0.1 -p $PgPort -U $PgUser -W -- $DemoDb
if ($LASTEXITCODE -ne 0) { throw 'Fresh DB creation failed; stop' }
psql -h 127.0.0.1 -p $PgPort -U $PgUser -W -d $DemoDb -v ON_ERROR_STOP=1 -f ClinicAppointmentBE/services/patient-service/src/main/resources/db/schema/uuid/V001__patient_uuid_empty.sql
if ($LASTEXITCODE -ne 0) { throw 'Fresh UUID schema failed; stop' }
$env:PATIENT_UUID_DB_URL = "jdbc:postgresql://127.0.0.1:${PgPort}/${DemoDb}"
$env:PATIENT_UUID_DB_USERNAME = $PgUser
$credential = Get-Credential -UserName $PgUser -Message 'Disposable demo DB credential'
$env:PATIENT_UUID_DB_PASSWORD = $credential.GetNetworkCredential().Password
```

No drop/reset/ALTER is supplied. Legacy data requires owner mapping. Hibernate validates the new schema and will not create/upgrade it.

## Configure loopback references

```powershell
$env:PATIENT_DOCTOR_REFERENCE_URI = 'http://localhost:8082/api/v1/doctors'
$env:PATIENT_APPOINTMENT_REFERENCE_URI = 'http://localhost:8083/internal/v1/appointments'
$doctorCredential = Get-Credential -Message 'Disposable Doctor service client'
$appointmentCredential = Get-Credential -Message 'Different disposable Appointment service client'
$env:PATIENT_DOCTOR_REFERENCE_CLIENT_ID = $doctorCredential.UserName
$env:PATIENT_DOCTOR_REFERENCE_CLIENT_SECRET = $doctorCredential.GetNetworkCredential().Password
$env:PATIENT_APPOINTMENT_REFERENCE_CLIENT_ID = $appointmentCredential.UserName
$env:PATIENT_APPOINTMENT_REFERENCE_CLIENT_SECRET = $appointmentCredential.GetNetworkCredential().Password
java -jar ClinicAppointmentBE/services/patient-service/target/patient-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=local,doctor-reference,appointment-reference --server.address=127.0.0.1 --server.port=8081
```

Credentials must differ from each other and from Auth/Scope. No real credentials are written to source or logs. Defaults keep reference clients OFF; `local` alone only supplies URIs. To turn reference clients OFF omit their profiles or set `--patient.reference.doctor.enabled=false --patient.reference.appointment.enabled=false`. HTTPS works by setting reference URI to https:// and supplying a valid CA/hostname trust chain (optional per-endpoint PKCS12 settings); no fallback/trust-all exists. Auth/Scope profiles must only be enabled when genuine approved `/2` authorities and HTTPS config are available. Their current absence must not be bypassed.

## Demonstrate health and fail-closed now

In another PowerShell:

```powershell
Invoke-RestMethod http://127.0.0.1:8081/actuator/health
curl.exe -i -H "Content-Type: application/json" -H "X-User-Role: ROLE_STAFF" --data '{}' http://127.0.0.1:8081/api/v3/patients
curl.exe -i -H "Content-Type: application/json" -H "Authorization: Bearer synthetic-no-authority" --data '{}' http://127.0.0.1:8081/api/v3/patients
```

Expected health UP, anonymous/forged identity401, unavailable identity503; all four tables unchanged. The packaged-JAR automated smoke asserts these exact outcomes on a fresh owned DB. Do not treat these denials as a completed clinical demo.

## Positive clinical flow after owners supply approved UUID providers

Use real-issued USER Doctor token with linked UUID, current clinical assignment and VALID_VISIT grant. POST `/api/v3/patients/{patientUuid}/medical-records`:

```json
{
  "doctorId": "550e8400-e29b-41d4-a716-446655440001",
  "appointmentId": "550e8400-e29b-41d4-a716-446655440002",
  "visitDate": "2026-11-01T09:00:00Z",
  "symptoms": "Sample symptoms",
  "diagnosis": "Sample diagnosis",
  "treatment": "Sample treatment",
  "notes": "Synthetic demo data"
}
```

IDs must be actual matching synthetic persisted facts from each owner, not arbitrary fixture substitution. VisitDate must not be future. Walk-in permits appointmentId null but still requires VALID_VISIT. Store returned record UUID and numeric version. GET nested record by UUID; PUT replaces clinical fields with version/changeReason, no immutable Doctor/Appointment keys. Reusing stale version yields409; cross-patient parent404; wrong author403; mismatched reference422; invalid UUID400; provider/version/wire failures503 with no clinical/revision writes. Tests exercise these flows with doubles and real PostgreSQL; real owner integration is pending.

Doctor/Appointment currently use Long identifiers and Appointment lacks selected protected route. Consequently HTTP reachability alone is not contract verification. Historical HTTPS B-04/B-05 results are old-contract evidence only. No production fake provider or mock Auth token is available to bypass these blockers.

## Applying reviewed patch later

Use a clean isolated checkout of the pinned bao commit, not the original dirty main. Check remote movement first, then `git apply --check <patient-http-uuid.patch>` and `git apply <patient-http-uuid.patch>`. Re-run Patient verify and review the exact manifest. New test sources may be ignored by upstream broad test ignores; retain them using the manifest allowlist, never `git add -A`/blanket force-add. No commit, push or PR update is part of this task.
