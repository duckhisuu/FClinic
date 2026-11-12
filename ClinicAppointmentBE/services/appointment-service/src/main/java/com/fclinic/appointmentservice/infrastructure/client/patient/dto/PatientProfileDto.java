package com.fclinic.appointmentservice.infrastructure.client.patient.dto;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record PatientProfileDto(String id, String patientCode, String fullName, String phoneNumber, String email,
    Object dateOfBirth, Object gender, Object address, String status, Object version, Object createdAt,
    Object updatedAt, Object createdBy, Object updatedBy) {}

