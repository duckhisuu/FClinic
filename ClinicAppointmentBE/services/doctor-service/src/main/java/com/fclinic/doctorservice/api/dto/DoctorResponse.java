package com.fclinic.doctorservice.api.dto;

public record DoctorResponse(
        Long id,
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
