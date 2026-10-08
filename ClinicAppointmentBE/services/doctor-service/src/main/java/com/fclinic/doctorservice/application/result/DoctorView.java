package com.fclinic.doctorservice.application.result;

public record DoctorView(
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
