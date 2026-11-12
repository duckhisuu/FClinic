package com.fclinic.appointmentservice.infrastructure.client.auth.dto;

public record ScopeDecisionDto(String contractVersion, ScopeQueryDto request, String evaluatedAt, Boolean allowed,
    java.util.List<String> patientIds, Evidence evidence) {
  public record Evidence(String assignmentId, Boolean active, String validFrom, String validUntil, String visitId) {}
}

