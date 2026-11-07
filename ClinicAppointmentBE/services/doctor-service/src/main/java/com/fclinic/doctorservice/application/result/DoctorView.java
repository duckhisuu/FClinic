package com.fclinic.doctorservice.application.result;

import java.util.UUID;

public record DoctorView(
        UUID id,
        String name,
        String specialty,
        String department,
        String qualification,
        Integer experienceYears,
        Double consultationFee,
        String roomNumber,
        String bio,
        String avatarUrl,
        boolean active
) {
}
