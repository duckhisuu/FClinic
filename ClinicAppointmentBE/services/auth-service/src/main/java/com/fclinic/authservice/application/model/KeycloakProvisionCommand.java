package com.fclinic.authservice.application.model;

import com.fclinic.authservice.domain.valueobject.RoleName;

import java.util.UUID;

public record KeycloakProvisionCommand(
        UUID fclinicUserId,
        String username,
        String email,
        String password,
        String fullName,
        RoleName roleName,
        UUID patientId,
        UUID doctorId,
        String phoneNumber,
        boolean enabled
) {
    @Override
    public String toString() {
        return "KeycloakProvisionCommand[username=" + username + ", roleName=" + roleName + "]";
    }
}

