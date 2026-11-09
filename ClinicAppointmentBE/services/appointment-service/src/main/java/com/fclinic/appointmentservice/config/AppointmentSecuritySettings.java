package com.fclinic.appointmentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.net.URI;

@ConfigurationProperties("appointment.security")
public record AppointmentSecuritySettings(URI introspectionUri, String issuer,
    @DefaultValue("appointment-service") String audience, String clientId, String clientSecret,
    URI scopeUri, String scopeClientId, String scopeClientSecret,
    String patientClientId, String patientClientSecret,
    String notificationClientId, String notificationClientSecret,
    URI patientTokenUri, String patientTokenClientId, String patientTokenClientSecret) {
  public AppointmentSecuritySettings {
    java.util.Set<String> secrets = new java.util.HashSet<>();
    for (String secret : new String[]{clientSecret, scopeClientSecret, patientClientSecret, notificationClientSecret, patientTokenClientSecret})
      if (secret != null && !secret.isBlank() && !secrets.add(secret))
        throw new IllegalArgumentException("Appointment security credentials must be distinct");
  }
  @Override public String toString() { return "AppointmentSecuritySettings[redacted]"; }
}
