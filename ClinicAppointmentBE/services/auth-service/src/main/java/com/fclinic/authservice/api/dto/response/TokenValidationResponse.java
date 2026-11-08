package com.fclinic.authservice.api.dto.response;

import java.util.UUID;

public record TokenValidationResponse(
        boolean valid,
        UUID userId,
        UUID keycloakId,
        UUID patientId,
        UUID doctorId,
        String username,
        String role,
        String email
) {
}

