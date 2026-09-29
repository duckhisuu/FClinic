package com.fclinic.doctorservice.application.result;

import com.fclinic.doctorservice.domain.aggregate.Doctor;

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
    public static DoctorView from(Doctor domain) {
        return new DoctorView(
                domain.getId(),
                domain.getName(),
                domain.getSpecialty(),
                domain.getDepartment(),
                domain.getQualification(),
                domain.getExperienceYears(),
                domain.getConsultationFee(),
                domain.getRoomNumber(),
                domain.getBio(),
                domain.getAvatarUrl(),
                domain.isActive()
        );
    }
}
