package com.fclinic.authservice.application.command;

import java.util.UUID;

public record UpdateUserCommand(
        String fullName,
        String email,
        String phoneNumber,
        String role,
        Boolean active,
        UUID patientId,
        UUID doctorId
) {
}

