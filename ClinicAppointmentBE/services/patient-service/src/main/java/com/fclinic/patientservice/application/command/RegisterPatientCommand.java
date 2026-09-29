package com.fclinic.patientservice.application.command;

import java.time.LocalDate;

public record RegisterPatientCommand(
        String fullName,
        String phoneNumber,
        String email,
        LocalDate dateOfBirth,
        String gender,
        String address
) {}
