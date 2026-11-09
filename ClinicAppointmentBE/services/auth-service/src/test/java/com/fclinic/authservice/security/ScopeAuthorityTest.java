package com.fclinic.authservice.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fclinic.authservice.application.port.out.*;
import com.fclinic.authservice.application.service.ScopeAuthorityService;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.valueobject.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class ScopeAuthorityTest {
  final Instant now = Instant.parse("2026-11-09T00:00:00Z");
  final UUID subject = UUID.randomUUID(), patient = UUID.randomUUID(), assignment = UUID.randomUUID();
  final UserRepositoryPort users = mock(UserRepositoryPort.class);
  final ScopeAssignmentPort assignments = mock(ScopeAssignmentPort.class);
  final ScopeAuthorityService service = new ScopeAuthorityService(users, assignments, Clock.fixed(now, ZoneOffset.UTC));
  final User staff = User.create(UUID.randomUUID(), subject, "synthetic_staff", "staff@example.test", null, "Synthetic",
      new Role(UUID.randomUUID(), RoleName.ROLE_STAFF, "", now, now), null, null, null);
  @BeforeEach void setup() {
    when(users.findByKeycloakId(subject)).thenReturn(Optional.of(staff));
    when(assignments.grants()).thenReturn(Map.of()); when(assignments.services()).thenReturn(Map.of());
  }
  ScopeAuthorityService.Query staffQuery(String operation, UUID patientId) {
    return new ScopeAuthorityService.Query("appointment-work-scope/1", UUID.randomUUID(), now, "ADMINISTRATIVE", operation,
        new ScopeAuthorityService.Caller(subject.toString(), "USER", null, null, null), patientId, null, null);
  }
  void grant(String operation, boolean active, Instant until) {
    when(assignments.grants()).thenReturn(Map.of(assignment, new ScopeAssignmentPort.Grant(subject.toString(), Set.of(operation), Set.of(patient),
        false, active, now.minusSeconds(60), until, null, null, null)));
  }
  @Test void staffRoleAloneDoesNotGrantAccess() { assertThat(service.evaluate(staffQuery("APPOINTMENT_CANCEL", patient))).containsEntry("allowed", false); }
  @Test void assignmentsAreResourceOperationAndTimeBound() {
    grant("APPOINTMENT_CANCEL", true, now.plusSeconds(60));
    assertThat(service.evaluate(staffQuery("APPOINTMENT_CANCEL", patient))).containsEntry("allowed", true);
    assertThat(service.evaluate(staffQuery("APPOINTMENT_CANCEL", UUID.randomUUID()))).containsEntry("allowed", false);
    assertThat(service.evaluate(staffQuery("APPOINTMENT_RESCHEDULE", patient))).containsEntry("allowed", false);
    grant("APPOINTMENT_CANCEL", true, now);
    assertThat(service.evaluate(staffQuery("APPOINTMENT_CANCEL", patient))).containsEntry("allowed", false);
  }
  @Test void accountDisableTakesEffectBeforeGrant() {
    grant("APPOINTMENT_CANCEL", true, now.plusSeconds(60)); staff.setActiveStatus(false);
    assertThat(service.evaluate(staffQuery("APPOINTMENT_CANCEL", patient))).containsEntry("allowed", false);
  }
  @Test void searchReturnsOnlyExplicitAssignedPatientsAndEchoesRequest() {
    grant("APPOINTMENT_SEARCH", true, now.plusSeconds(60)); var query = staffQuery("APPOINTMENT_SEARCH", null);
    var result = service.evaluate(query);
    assertThat(result).containsEntry("allowed", true).containsEntry("request", query).containsEntry("patientIds", Set.of(patient));
    assertThat((Map<?, ?>) result.get("evidence")).hasSize(5);
  }
  @Test void serviceRegistryAndGrantAreBothRequired() {
    String machine = UUID.randomUUID().toString();
    var q = new ScopeAuthorityService.Query("patient-work-scope/2", UUID.randomUUID(), now, "ADMINISTRATIVE", "APPOINTMENT_PROFILE_READ",
        new ScopeAuthorityService.Caller(machine, "SERVICE", null, null, "appointment-service"), patient, null, null);
    when(assignments.grants()).thenReturn(Map.of(assignment, new ScopeAssignmentPort.Grant(machine, Set.of("APPOINTMENT_PROFILE_READ"), Set.of(),
        true, true, now.minusSeconds(60), now.plusSeconds(60), null, null, null)));
    assertThat(service.evaluate(q)).containsEntry("allowed", false);
    when(assignments.services()).thenReturn(Map.of("appointment", new ScopeAssignmentPort.ServiceIdentity(machine, "appointment-service", Set.of("SVC_PATIENT_READ"))));
    assertThat(service.evaluate(q)).containsEntry("allowed", true);
    var forged = new ScopeAuthorityService.Query(q.contractVersion(), q.nonce(), q.requestedAt(), q.authority(), q.operation(),
        new ScopeAuthorityService.Caller(machine, "SERVICE", null, null, "notification-service"), patient, null, null);
    assertThat(service.evaluate(forged)).containsEntry("allowed", false);
  }
  @Test void staleAssertionsAndSpoofedMappingDeny() {
    grant("APPOINTMENT_READ", true, now.plusSeconds(60)); var q = staffQuery("APPOINTMENT_READ", patient);
    var stale = new ScopeAuthorityService.Query(q.contractVersion(), q.nonce(), now.minusSeconds(6), q.authority(), q.operation(), q.caller(), patient, null, null);
    assertThat(service.evaluate(stale)).containsEntry("allowed", false);
    var spoofed = new ScopeAuthorityService.Query(q.contractVersion(), q.nonce(), now, q.authority(), q.operation(),
        new ScopeAuthorityService.Caller(subject.toString(), "USER", patient, null, null), patient, null, null);
    assertThat(service.evaluate(spoofed)).containsEntry("allowed", false);
  }
  @Test void walkInVisitUsesIndependentVisitEvidenceAndExactVisitDate() {
    UUID doctorId = UUID.randomUUID(), visitId = UUID.randomUUID();
    var doctor = User.create(UUID.randomUUID(), subject, "synthetic_doctor", "doctor@example.test", null, "Doctor",
        new Role(UUID.randomUUID(), RoleName.ROLE_DOCTOR, "", now, now), null, doctorId, null);
    when(users.findByKeycloakId(subject)).thenReturn(Optional.of(doctor));
    when(assignments.grants()).thenReturn(Map.of(assignment, new ScopeAssignmentPort.Grant(subject.toString(), Set.of("VALID_VISIT"),
        Set.of(patient), false, true, now.minusSeconds(60), now.plusSeconds(60), null, now, visitId)));
    var caller = new ScopeAuthorityService.Caller(subject.toString(), "USER", null, doctorId, null);
    var query = new ScopeAuthorityService.Query("patient-work-scope/2", UUID.randomUUID(), now, "CLINICAL", "VALID_VISIT", caller, patient, null, now);
    var result = service.evaluate(query); assertThat(result).containsEntry("allowed", true);
    assertThat(((Map<?, ?>) result.get("evidence")).get("visitId")).isEqualTo(visitId);
    var wrongDate = new ScopeAuthorityService.Query(query.contractVersion(), query.nonce(), now, query.authority(), query.operation(), caller, patient, null, now.minusSeconds(1));
    assertThat(service.evaluate(wrongDate)).containsEntry("allowed", false);
  }
}
