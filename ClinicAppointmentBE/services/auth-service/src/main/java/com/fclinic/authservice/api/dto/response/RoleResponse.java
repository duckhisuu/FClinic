package com.fclinic.authservice.api.dto.response;

import java.util.UUID;

public record RoleResponse(
        UUID id,
        String roleName,
        String description
) {
}

