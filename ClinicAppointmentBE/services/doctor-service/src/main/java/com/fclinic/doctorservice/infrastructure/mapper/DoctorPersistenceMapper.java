package com.fclinic.doctorservice.infrastructure.mapper;

import com.fclinic.doctorservice.domain.aggregate.Doctor;
import com.fclinic.doctorservice.infrastructure.entity.JpaDoctorEntity;

public final class DoctorPersistenceMapper {

    private DoctorPersistenceMapper() {
    }

    public static JpaDoctorEntity toEntity(Doctor doctor) {
        return new JpaDoctorEntity(doctor.getId(), doctor.getName(), doctor.getSpecialty(), doctor.getDepartment(),
                doctor.getQualification(), doctor.getExperienceYears(), doctor.getConsultationFee(),
                doctor.getRoomNumber(), doctor.getBio(), doctor.getAvatarUrl(), doctor.isActive(),
                doctor.getCreatedAt(), doctor.getUpdatedAt());
    }

    public static Doctor toDomain(JpaDoctorEntity entity) {
        return new Doctor(entity.getId(), entity.getName(), entity.getSpecialty(), entity.getDepartment(),
                entity.getQualification(), entity.getExperienceYears(), entity.getConsultationFee(),
                entity.getRoomNumber(), entity.getBio(), entity.getAvatarUrl(), entity.isActive(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
