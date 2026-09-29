package com.fclinic.doctorservice.application.command;

public record CreateDoctorCommand(
        String name,
        String specialty,
        String department,
        String qualification,
        Integer experienceYears,
        Double consultationFee,
        String roomNumber,
        String bio,
        String avatarUrl
) {}
