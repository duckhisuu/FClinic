package com.fclinic.authservice.application.model;

import java.util.UUID;

public record TokenValidationResult(
        boolean valid,
        UUID userId,
        UUID keycloakId,
        UUID patientId,
        UUID doctorId,
        String username,
        String role,
        String email
) {
    public static TokenValidationResult invalid() {
        return new TokenValidationResult(false, null, null, null, null, null, null, null);
    }
}

