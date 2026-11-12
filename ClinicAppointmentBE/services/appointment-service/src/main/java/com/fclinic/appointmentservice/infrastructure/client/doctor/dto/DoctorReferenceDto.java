package com.fclinic.appointmentservice.infrastructure.client.doctor.dto;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record DoctorReferenceDto(String id, String name, String specialty, Object department, Object qualification,
    Object experienceYears, java.math.BigDecimal consultationFee, Object roomNumber, Object bio, Object avatarUrl, Boolean active) {}

