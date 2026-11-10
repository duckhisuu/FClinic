package com.fclinic.authservice.api.controller;

import com.fclinic.authservice.application.model.AuthenticatedCaller;
import com.fclinic.authservice.application.port.out.ScopeAssignmentPort;
import com.fclinic.authservice.application.service.ScopeAuthorityService;
import com.fclinic.common.web.ApiResponse;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** UI hints only. Patient re-evaluates every request against its own policy. */
@RestController
@RequestMapping("/api/v1/auth/me/patient-capabilities")
public class PatientCapabilitiesController {
    private final ScopeAssignmentPort assignments;
    private final ScopeAuthorityService scopes;
    private final Clock clock;
    public PatientCapabilitiesController(ScopeAssignmentPort assignments, ScopeAuthorityService scopes, Clock clock) {
        this.assignments = assignments; this.scopes = scopes; this.clock = clock;
    }
    @GetMapping
    public ApiResponse<Map<String, Object>> get(@AuthenticationPrincipal AuthenticatedCaller caller) {
        var identity = new ScopeAuthorityService.Caller(caller.keycloakId().toString(), "USER", caller.patientId(), caller.doctorId(), null);
        Set<UUID> candidates = new HashSet<>();
        assignments.grants().values().stream().filter(g -> identity.subject().equals(g.subject()) && !g.allPatients())
                .forEach(g -> candidates.addAll(g.patientIds()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("createPatient", caller.patientId() == null && caller.role() == com.fclinic.authservice.domain.valueobject.RoleName.ROLE_PATIENT);
        result.put("searchPatients", (allowed(identity, "ADMINISTRATIVE", "ADMIN_ASSIGNMENT_SEARCH", null)
                && allowed(identity, "ADMINISTRATIVE", "ADMIN_SEARCH", null)) || allowed(identity, "CLINICAL", "DOCTOR_SEARCH", null));
        result.put("updatePatientIds", caller.patientId() != null && caller.role() == com.fclinic.authservice.domain.valueobject.RoleName.ROLE_PATIENT
                ? List.of(caller.patientId()) : List.of());
        result.put("createRecordPatientIds", candidates.stream().filter(id -> allowed(identity, "CLINICAL", "MEDICAL_CREATE", id)).toList());
        result.put("updateRecordPatientIds", candidates.stream().filter(id -> allowed(identity, "CLINICAL", "MEDICAL_UPDATE", id)).toList());
        return ApiResponse.success(result);
    }
    private boolean allowed(ScopeAuthorityService.Caller caller, String authority, String operation, UUID patient) {
        return Boolean.TRUE.equals(scopes.evaluate(new ScopeAuthorityService.Query("patient-work-scope/2", UUID.randomUUID(),
                clock.instant(), authority, operation, caller, patient, null, null)).get("allowed"));
    }
}
