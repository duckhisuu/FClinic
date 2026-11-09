package com.fclinic.appointmentservice.application;

import java.util.Set;
import java.util.UUID;

/** Verified request context. Only the identity adapter constructs this from external credentials. */
public record AppointmentActor(String subject, String principalType, Set<String> authorities,
    UUID patientId, UUID doctorId, String serviceName, long deadlineNanos) {
  public AppointmentActor { authorities = authorities == null ? Set.of() : Set.copyOf(authorities); }
  public boolean user(String role) { return "USER".equals(principalType) && authorities.contains(role); }
  public String auditRole() {
    if (user("ROLE_PATIENT")) return "PATIENT";
    if (user("ROLE_STAFF")) return "STAFF";
    if (user("ROLE_DOCTOR")) return "DOCTOR";
    return "SYSTEM";
  }
}
