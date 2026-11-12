package com.fclinic.appointmentservice.infrastructure.client.auth.dto;

public record IntrospectionResponseDto(Boolean active, String contractVersion, String iss, java.util.List<String> aud,
    Long exp, String sub, String principalType, String userId, String patientId, String doctorId, java.util.List<String> authorities) {}

