# GitHub sync — 2026-11-09

- Primary: D:\Hoc_tap\26_FALL\MSS301\Project_Group\FClinic_Primary
- Branch: feature/patient-react-ui
- Before: 9ef2d4ba57f9789290e3d5ebce59c86c7abdfaec (actual; newer than last-known b14fa8a).
- Origin: https://github.com/duckhisuu/FClinic.git
- Fetch: git fetch origin --prune succeeded.
- Verified origin/main: 4e7d36169dad591e37b57e3ea32e0e64113fc8f6
- Merge base before sync: 9ef2d4ba57f9789290e3d5ebce59c86c7abdfaec; ahead/behind 0/10.
- Fast-forward: git merge --no-edit origin/main. HEAD after sync: 4e7d36169dad591e37b57e3ea32e0e64113fc8f6.
- Initial tracked/untracked working tree clean; ignored IDE/dependencies/build/evidence directories preserved. No stash, reset, clean, clone or branch creation.
- No pending merge/rebase; no unmerged paths. Conflict count 0.
- Original FClinic and SafeSync were not modified or used for development. No push.

## Integration changes after sync

- .gitignore
- ClinicAppointmentBE/infra/api-gateway/src/main/java/com/fclinic/apigateway/config/CorsConfig.java
- ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/api/controller/PatientCapabilitiesController.java
- ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/application/service/AuthApplicationService.java
- ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/infrastructure/config/SecurityConfig.java
- ClinicAppointmentBE/services/auth-service/src/main/resources/application.yml
- ClinicAppointmentBE/services/auth-service/src/test/java/com/fclinic/authservice/security/PatientCapabilitiesTest.java
- ClinicAppointmentBE/services/auth-service/src/test/java/com/fclinic/authservice/security/PublicIdentityBoundaryTest.java
- ClinicAppointmentFE/src/App.jsx
- ClinicAppointmentFE/src/appointment/AppointmentHistory.jsx
- ClinicAppointmentFE/src/appointment/api.js
- ClinicAppointmentFE/src/components/NotificationDrawer.jsx
- ClinicAppointmentFE/src/context/AuthContext.jsx
- ClinicAppointmentFE/src/pages/DoctorDashboardPage.jsx
- ClinicAppointmentFE/src/patient/PatientModule.jsx
- ClinicAppointmentFE/src/patient/__tests__/PreviewHarness.jsx
- ClinicAppointmentFE/src/patient/__tests__/auth-context.test.jsx
- ClinicAppointmentFE/src/patient/__tests__/notification.test.jsx
- ClinicAppointmentFE/src/patient/__tests__/session.test.js
- ClinicAppointmentFE/src/patient/__tests__/ui.test.jsx
- ClinicAppointmentFE/src/patient/api/patientApi.js
- ClinicAppointmentFE/src/patient/components/MedicalRecordForm.jsx
- ClinicAppointmentFE/src/patient/pages/MedicalRecordDetailPage.jsx
- ClinicAppointmentFE/src/patient/pages/MedicalRecordsPage.jsx
- ClinicAppointmentFE/src/services/api.js
- ClinicAppointmentFE/src/services/authApi.js
- ClinicAppointmentFE/src/services/session.js
- ClinicAppointmentFE/vite.config.js
- PATIENT_REACT_UI_START_POINT.md
- README.md

Integration reports, startup/stop/acceptance scripts, redacted HTTP/DB evidence and three browser screenshots are in docs/integration/. New regression tests were explicitly added despite the repository’s existing broad test-ignore rules. Local runtime credentials/certificates/state remain ignored. A local integration commit will preserve this reviewed state; no remote push.
