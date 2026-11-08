package com.fclinic.authservice.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        UUID keycloakId,
        UUID patientId,
        UUID doctorId,
        String username,
        String email,
        String phoneNumber,
        String fullName,
        String role,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}

