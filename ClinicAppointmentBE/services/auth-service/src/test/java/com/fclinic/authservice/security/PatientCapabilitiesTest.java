package com.fclinic.authservice.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fclinic.authservice.api.controller.PatientCapabilitiesController;
import com.fclinic.authservice.application.model.AuthenticatedCaller;
import com.fclinic.authservice.application.port.out.*;
import com.fclinic.authservice.application.service.ScopeAuthorityService;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.valueobject.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PatientCapabilitiesTest {
    @Test void capabilitiesRequireExactActiveAssignmentsAndVerifiedIdentity() {
        var now = Instant.parse("2026-11-09T00:00:00Z");
        var subject = UUID.randomUUID(); var patient = UUID.randomUUID();
        var users = mock(UserRepositoryPort.class); var assignments = mock(ScopeAssignmentPort.class);
        var user = User.create(UUID.randomUUID(), subject, "staff", "staff@example.test", null, "Staff",
                new Role(UUID.randomUUID(), RoleName.ROLE_STAFF, "", now, now), null, null, null);
        when(users.findByKeycloakId(subject)).thenReturn(Optional.of(user));
        when(assignments.grants()).thenReturn(Map.of(UUID.randomUUID(), new ScopeAssignmentPort.Grant(subject.toString(),
                Set.of("ADMIN_ASSIGNMENT_CREATE", "ADMIN_ASSIGNMENT_UPDATE", "ADMIN_PROFILE_UPDATE"), Set.of(patient), false, true,
                now.minusSeconds(1), now.plusSeconds(60), null, null, null)));
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var controller = new PatientCapabilitiesController(assignments, new ScopeAuthorityService(users, assignments, clock), clock);
        var caller = new AuthenticatedCaller(user.getId(), subject, "staff", "staff@example.test", RoleName.ROLE_STAFF, null, null);
        var result = controller.get(caller).data();
        assertThat(result).containsEntry("createPatient", true).containsEntry("searchPatients", false);
        assertThat(result.get("updatePatientIds")).isEqualTo(List.of(patient));
        assertThat((List<?>)result.get("createRecordPatientIds")).isEmpty();
        assertThat((List<?>)result.get("readRecordPatientIds")).isEmpty();
        user.setActiveStatus(false);
        assertThat(controller.get(caller).data()).containsEntry("createPatient", false);
        assertThat((List<?>)controller.get(caller).data().get("updatePatientIds")).isEmpty();
    }
    @Test void readOnlyDoctorCapabilitiesDoNotGrantClinicalWritesOrOtherPatients() {
        var now = Instant.parse("2026-11-10T00:00:00Z");
        var subject = UUID.randomUUID(); var patient = UUID.randomUUID();
        var users = mock(UserRepositoryPort.class); var assignments = mock(ScopeAssignmentPort.class);
        var user = User.create(UUID.randomUUID(), subject, "reader", "reader@example.test", null, "Reader",
                new Role(UUID.randomUUID(), RoleName.ROLE_DOCTOR, "", now, now), null, UUID.randomUUID(), null);
        when(users.findByKeycloakId(subject)).thenReturn(Optional.of(user));
        when(assignments.grants()).thenReturn(Map.of(UUID.randomUUID(), new ScopeAssignmentPort.Grant(subject.toString(),
                Set.of("MEDICAL_READ"), Set.of(patient), false, true, now.minusSeconds(1), now.plusSeconds(60), null, null, null)));
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var controller = new PatientCapabilitiesController(assignments, new ScopeAuthorityService(users, assignments, clock), clock);
        var caller = new AuthenticatedCaller(user.getId(), subject, "reader", user.getEmail(), user.getRoleName(), null, user.getDoctorId());
        var result = controller.get(caller).data();
        assertThat(result.get("readRecordPatientIds")).isEqualTo(List.of(patient));
        assertThat((List<?>)result.get("createRecordPatientIds")).isEmpty();
        assertThat((List<?>)result.get("updateRecordPatientIds")).isEmpty();
    }
}
