package com.fclinic.doctorservice.api.dto;

import java.util.UUID;

public record DoctorResponse(
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
