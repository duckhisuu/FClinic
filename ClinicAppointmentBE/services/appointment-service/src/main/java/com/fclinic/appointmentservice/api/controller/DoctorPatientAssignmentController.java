package com.fclinic.appointmentservice.api.controller;

import com.fclinic.appointmentservice.api.AppointmentAuthentication;
import com.fclinic.appointmentservice.application.usecase.DoctorPatientAssignmentService;
import com.fclinic.appointmentservice.infrastructure.security.AuthIdentityVerifier;
import com.fclinic.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/doctor-patient-assignments")
public class DoctorPatientAssignmentController {
    public record AssignedPatients(UUID doctorId, List<UUID> patientIds) { }
    private final DoctorPatientAssignmentService service;
    private final String clientId, secret;
    public DoctorPatientAssignmentController(DoctorPatientAssignmentService service,
            @Value("${AUTH_APPOINTMENT_ASSIGNMENTS_CLIENT_ID:auth-doctor-patient-assignments}") String clientId,
            @Value("${AUTH_APPOINTMENT_ASSIGNMENTS_CLIENT_SECRET:}") String secret) {
        this.service = service; this.clientId = clientId; this.secret = secret;
    }
    @GetMapping("/{doctorId}")
    public ResponseEntity<ApiResponse<AssignedPatients>> get(HttpServletRequest request, @PathVariable UUID doctorId) {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.size() != 1) throw AuthIdentityVerifier.unauthorized();
        AppointmentAuthentication.authenticateBasic(headers.getFirst(), clientId, secret);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(ApiResponse.success(new AssignedPatients(doctorId, service.assignedPatients(doctorId))));
    }
}
