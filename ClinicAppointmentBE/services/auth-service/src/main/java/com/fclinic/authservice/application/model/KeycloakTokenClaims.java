package com.fclinic.authservice.application.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record KeycloakTokenClaims(
        boolean active,
        String subject,
        UUID keycloakUserId,
        String username,
        String email,
        String fullName,
        String issuer,
        List<String> audiences,
        long expiresAtEpochSecond,
        String authorizedParty,
        Set<String> authorities,
        UUID fclinicUserId,
        UUID patientId,
        UUID doctorId,
        boolean serviceAccount
) {
    public static KeycloakTokenClaims inactive() {
        return new KeycloakTokenClaims(
                false, null, null, null, null, null, null,
                List.of(), 0L, null, Set.of(), null, null, null, false
        );
    }
}

