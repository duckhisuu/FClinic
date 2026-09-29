package com.fclinic.doctorservice.api.dto;

import com.fclinic.doctorservice.application.result.DoctorView;

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
    public static DoctorResponse from(DoctorView view) {
        return new DoctorResponse(
                view.id(),
                view.name(),
                view.specialty(),
                view.department(),
                view.qualification(),
                view.experienceYears(),
                view.consultationFee(),
                view.roomNumber(),
                view.bio(),
                view.avatarUrl(),
                view.active()
        );
    }
}
