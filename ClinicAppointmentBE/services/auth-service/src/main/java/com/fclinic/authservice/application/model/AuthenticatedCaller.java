package com.fclinic.authservice.application.model;

import com.fclinic.authservice.domain.valueobject.RoleName;

import java.util.UUID;

public record AuthenticatedCaller(
        UUID userId,
        UUID keycloakId,
        String username,
        String email,
        RoleName role,
        UUID patientId,
        UUID doctorId
) {
}

