package com.fclinic.appointmentservice.infrastructure.client.auth.dto;

public record ScopeQueryDto(String contractVersion, String nonce, String requestedAt, String authority, String operation,
    Caller caller, String patientId, String appointmentId, String visitDate) {
  public record Caller(String subject, String principalType, String patientId, String doctorId, String serviceName) {}
}

