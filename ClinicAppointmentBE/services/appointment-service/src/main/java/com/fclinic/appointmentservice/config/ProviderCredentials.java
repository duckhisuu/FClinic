package com.fclinic.appointmentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Dedicated read-only SERVICE credentials, independent of human and Patient OAuth tokens. */
@ConfigurationProperties("appointment.providers")
public record ProviderCredentials(String doctorClientId, String doctorClientSecret,
    String recipientClientId, String recipientClientSecret) {
  public ProviderCredentials {
    if (doctorClientSecret != null && !doctorClientSecret.isBlank()
        && doctorClientSecret.equals(recipientClientSecret))
      throw new IllegalArgumentException("Provider secrets must be distinct");
  }
  @Override public String toString() { return "ProviderCredentials[redacted]"; }
}
