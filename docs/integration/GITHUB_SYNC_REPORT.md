# GitHub sync — Appointment + Notification, 2026-11-09

- Primary: D:\Hoc_tap\26_FALL\MSS301\Project_Group\FClinic_Primary
- Branch: feature/patient-react-ui. Before: 07d8afd5f59a32f2e128859714f0cb645490404d.
- Origin: https://github.com/duckhisuu/FClinic.git. git fetch origin --prune succeeded.
- Verified main: e94d652becdea6ccc4cc19613f1a925523d43a91.
- Merge base: 4e7d36169dad591e37b57e3ea32e0e64113fc8f6. Ahead/behind 1/1, so history-preserving merge was required.
- Main merge commit: a608f6229a856054cd39eaf3ecfaced874a75ce1. main is an ancestor of HEAD. Two conflicting files resolved; no unmerged paths.
- Untracked docs/design and ignored runtime/config/dependencies preserved. Snapshot/status/hashes/ZIP: .runtime/sync-20261109-230340. No stash/reset/clean/clone/rebase/squash.
- Notification ref: 316944c9f16d50eb8fd50d82a2184cf478d6b494. Branch merge base with main: 72be8fd335eecba71f6db924c05164e6c4dfbb82.
- Neither the whole diverged Notification branch nor unsafe 860b433 was merged/cherry-picked. 860b433 is not an ancestor. No Auth HTTP/mock fallback received.
- The Notification module at HEAD exactly matched 316944c's parent. Reviewed changed-file hunks from 316944c were applied using git apply --check then git apply. FE adapters and the audience mapper were integrated separately into current implementations.
- Automatic approval review rejected a whole-module restore; it was not executed. The safer patch application preserved current files and changed only reviewed hunks.
- Original FClinic and SafeSync untouched. No GitHub push or remote main merge.

## Migration and runtime

V4/V5 tested on restored test DB copies before starting the new consumer. Current local source DB contained 0 notifications and 8 templates. A synthetic numeric row on a test copy survived in notifications_legacy_id_archive (1 row), active rows 0, templates 8. The real DB then migrated through Flyway normally. Backup dumps and test-only copies retained; no volume/realm reset.

Keycloak's existing fclinic-claims scope received only the named Notification audience mapper; startup checks it idempotently. Auth/Patient TLS verification retained. Existing Vite :5173 was identified as Primary from its served module path and reused, not killed or duplicated.

## Results

Reactor verify 379 tests PASS; Patient-only verify PASS; 13 contracts PASS; frontend 46 tests PASS/build PASS. Real HTTP: onboarding/operator provisioning/booking/Notification 54, broker 7, Appointment edge 11, Patient 25, authorization 7, provider outage 3 checks PASS. Browser booking/history/inbox/account switch verified; all eight health checks PASS. Overall PARTIAL: public trusted onboarding remains blocked, detailed in REMAINING_ISSUES.md.

Final local integration commit is identified by git log -1; this report is included in that commit. Design deliverables remain separately untracked and preserved.

## Files changed in this sync/integration
- .gitignore
- ClinicAppointmentBE/infra/keycloak/fclinic-realm.json
- ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/api/controller/AuthController.java
- ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/api/dto/request/LinkPatientProfileRequest.java
- ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/application/port/in/UpdateProfileUseCase.java
- ClinicAppointmentBE/services/auth-service/src/main/java/com/fclinic/authservice/application/service/AuthApplicationService.java
- ClinicAppointmentBE/services/auth-service/src/test/java/com/fclinic/authservice/security/PublicIdentityBoundaryTest.java
- ClinicAppointmentBE/services/notification-service/README.md
- ClinicAppointmentBE/services/notification-service/pom.xml
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/api/controller/NotificationController.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/api/dto/NotificationResponse.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/dto/AppointmentSnapshotDto.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/dto/RecipientDto.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/dto/event/AppointmentNotificationEvent.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/dto/event/EventRecipientDto.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/exception/NotificationNotFoundException.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/exception/NotificationNotRetryableException.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/port/in/DeliverNotificationUseCase.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/port/in/QueryNotificationsUseCase.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/port/out/NotificationRepositoryPort.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/result/NotificationView.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/usecase/NotificationDeliveryService.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/usecase/NotificationIngestionService.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/usecase/NotificationQueryService.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/application/usecase/NotificationStateService.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/config/NotificationSecurityConfiguration.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/domain/aggregate/Notification.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/infrastructure/adapter/JpaNotificationRepositoryAdapter.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/infrastructure/entity/JpaNotificationEntity.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/infrastructure/entity/JpaNotificationTemplateEntity.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/infrastructure/persistence/SpringDataJpaNotificationRepository.java
- ClinicAppointmentBE/services/notification-service/src/main/java/com/fclinic/notificationservice/infrastructure/persistence/SpringDataJpaNotificationTemplateRepository.java
- ClinicAppointmentBE/services/notification-service/src/main/resources/application.yml
- ClinicAppointmentBE/services/notification-service/src/main/resources/db/migration/V4__uuid_recipient_isolation.sql
- ClinicAppointmentBE/services/notification-service/src/main/resources/db/migration/V5__notification_uuid_primary_key.sql
- ClinicAppointmentBE/services/notification-service/src/test/java/com/fclinic/notificationservice/api/NotificationIsolationTest.java
- ClinicAppointmentBE/services/notification-service/src/test/java/com/fclinic/notificationservice/application/NotificationDeliveryServiceTest.java
- ClinicAppointmentBE/services/notification-service/src/test/java/com/fclinic/notificationservice/application/NotificationIngestionServiceTest.java
- ClinicAppointmentBE/services/notification-service/src/test/java/com/fclinic/notificationservice/application/NotificationStateServiceTest.java
- ClinicAppointmentBE/services/notification-service/src/test/java/com/fclinic/notificationservice/config/NotificationSecurityConfigurationTest.java
- ClinicAppointmentBE/services/notification-service/src/test/java/com/fclinic/notificationservice/domain/NotificationTest.java
- ClinicAppointmentBE/services/patient-service/src/main/java/com/fclinic/patientservice/application/security/PatientPolicy.java
- ClinicAppointmentBE/services/patient-service/src/test/java/com/fclinic/patientservice/PatientPolicyTest.java
- ClinicAppointmentFE/src/App.jsx
- ClinicAppointmentFE/src/appointment/AppointmentHistory.jsx
- ClinicAppointmentFE/src/appointment/api.js
- ClinicAppointmentFE/src/components/NotificationDrawer.jsx
- ClinicAppointmentFE/src/context/AuthContext.jsx
- ClinicAppointmentFE/src/notification/__tests__/notifications.test.jsx
- ClinicAppointmentFE/src/pages/PatientOnboardingPage.jsx
- ClinicAppointmentFE/src/services/api.js
- ClinicAppointmentFE/src/services/authApi.js
- ClinicAppointmentFE/vitest.config.js
- docs/integration/CONFLICT_RESOLUTION.md
- docs/integration/GITHUB_SYNC_REPORT.md
- docs/integration/PATIENT_ACCEPTANCE_TESTS.md
- docs/integration/REMAINING_ISSUES.md
- docs/integration/Start-FClinic.ps1
- docs/integration/Test-OnboardingNotification.cjs
- docs/integration/appointment-edge-evidence.json
- docs/integration/authorization-negative-evidence.json
- docs/integration/browser-database-evidence.json
- docs/integration/notification-broker-acceptance.json
- docs/integration/notification-uuid-browser.jpg
- docs/integration/onboarding-notification-evidence.json
- docs/integration/onboarding-trusted-provisioning-browser.jpg
- docs/integration/outage-database-evidence.json
- docs/integration/patient-booking-browser.jpg
- docs/integration/provider-outage-evidence.json
- docs/integration/runtime-http-evidence.json
- docs/integration/sync-runtime-health.json
- docs/integration/test-counts.json
- docs/integration/unauthorized-database-evidence.json
- ClinicAppointmentFE/src/appointment/__tests__/onboarding.test.jsx
