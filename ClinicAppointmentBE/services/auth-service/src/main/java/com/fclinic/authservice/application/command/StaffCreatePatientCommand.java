package com.fclinic.authservice.application.command;

import java.util.UUID;

public record StaffCreatePatientCommand(
        String fullName,
        String phoneNumber,
        String email,
        String initialPassword,
        UUID patientId
) {
    @Override
    public String toString() {
        return "StaffCreatePatientCommand[email=" + email + ", phoneNumber=" + phoneNumber + "]";
    }
}

