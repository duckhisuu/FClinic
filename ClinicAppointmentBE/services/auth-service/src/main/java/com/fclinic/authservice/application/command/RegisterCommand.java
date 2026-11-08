package com.fclinic.authservice.application.command;

import java.util.UUID;

public record RegisterCommand(
        String username,
        String password,
        String fullName,
        String email,
        String phoneNumber,
        UUID patientId
) {
    @Override
    public String toString() {
        return "RegisterCommand[username=" + username + ", email=" + email + "]";
    }
}

