package com.fclinic.authservice.application.command;

import java.util.UUID;

public record CreateUserCommand(
        String username,
        String password,
        String fullName,
        String email,
        String phoneNumber,
        String role,
        UUID patientId,
        UUID doctorId
) {
    @Override
    public String toString() {
        return "CreateUserCommand[username=" + username + ", role=" + role + "]";
    }
}

