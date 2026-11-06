# Patient v3 binding matrix

Audited before implementation at b14fa8af0ab8d3110eeb31356a4859acfb11839d: PatientController, PatientInput, PatientResponse, MedicalRecordResponse, PatientPolicy, SearchQuery/PageResult, GlobalExceptionHandler. Backend remains unchanged.

| Operation | Method/path | Request (allowlist) | Response data | Authorization |
|---|---|---|---|---|
| Create profile | POST /api/v3/patients | fullName, phoneNumber, email?, dateOfBirth?, gender?, address?; no status/version/id | PatientResponse, HTTP 201 | verified STAFF + receptionist CREATE |
| Search | GET /api/v3/patients | keyword ≤255 OR phoneNumber normalized; page≥0, size 1..100, sort fullName/createdAt asc/desc | PageResult | STAFF with administrative scopes OR DOCTOR with clinical patient scope |
| Detail | GET /api/v3/patients/{UUIDv4} | none | PatientResponse | own PATIENT, scoped STAFF/DOCTOR, specifically scoped appointment SERVICE |
| Replace profile | PUT /api/v3/patients/{UUIDv4} | complete fullName, phoneNumber, email, dateOfBirth, gender, address, status, version | PatientResponse | scoped STAFF; optimistic version |
| Create record | POST /api/v3/patients/{UUIDv4}/medical-records | doctorId UUIDv4, appointmentId UUIDv4/null, visitDate offset datetime, symptoms, diagnosis, treatment?, notes? | MedicalRecordResponse, HTTP 201 | bound DOCTOR + clinical CREATE + genuine VALID_VISIT; active doctor/provider references |
| List records | GET .../medical-records | page, size, sort visitDate asc/desc; optional appointmentId UUIDv4 | PageResult | own PATIENT OR clinical READ DOCTOR |
| Record detail | GET .../medical-records/{UUIDv4} | none | MedicalRecordResponse | same clinical read policy |
| Replace record | PUT .../medical-records/{UUIDv4} | visitDate, symptoms, diagnosis, treatment, notes, version, changeReason; NO doctorId/appointmentId | MedicalRecordResponse | clinical UPDATE DOCTOR; immutable author subject + doctor UUID; optimistic version |

PatientResponse: id, patientCode, fullName, phoneNumber, email, dateOfBirth, gender, address, status, version, createdAt, updatedAt, createdBy, updatedBy. MedicalRecordResponse: id, patientId, doctorId, appointmentId, visitDate, symptoms, diagnosis, treatment, notes, version, timestamps/actors. IDs remain canonical lowercase UUIDv4. Response fields are not blindly serialized as requests.

Success envelope exactly status/message/data; status must agree with HTTP. PageResult: content/page/size/totalElements/totalPages. Typed errors: status/message/data:null/code/fieldErrors[{field,message}], 400/401/403/404/409/422/503/500. Display safe localized text; preserve typed field errors and conflict/reload actions. No mock fallback.

Profile: fullName required ≤255 Unicode code points; phone accepts ≤40 input chars of +digits spaces ()-, normalizes 0xxxxxxxxx → +84xxxxxxxxx then E.164 8..15 digits; email nullable ≤255; DOB YYYY-MM-DD, real calendar date, not after today Asia/Saigon; gender MALE/FEMALE/OTHER/UNKNOWN; address nullable ≤2000; status ACTIVE/INACTIVE on PUT only. Version numeric, unchanged from last server read; client refuses unsafe integers.

Clinical: visitDate must contain offset, not future, backend truncates to microseconds; symptoms/diagnosis required ≤2000; treatment/notes nullable ≤4000; changeReason required on PUT ≤1000. Unknown fields rejected. Null appointment does not confer VALID_VISIT. The Patient v3 Doctor UUID contract is retained; Doctor service Long is deferred, never converted. Live clinical mutations are deliberately disabled in this phase. Preview is synthetic, isolated, development-only and uses the same input allowlists/validation.

Live session: only a host-provided session getAccessToken() may supply Bearer credentials. No token input, storage, JWT decoding, fake roles or production authorization fixture. Missing host session is shown as disconnected. Profile mutation buttons default denied without host capability hints; backend remains authoritative. Auth/WorkScope/provider E2E is not claimed.
