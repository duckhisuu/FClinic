package com.fclinic.appointmentservice.infrastructure.client.doctor.dto;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record DoctorSlotDto(String id, String doctorId, String slotDate, String startTime, String endTime, Boolean published) {}

