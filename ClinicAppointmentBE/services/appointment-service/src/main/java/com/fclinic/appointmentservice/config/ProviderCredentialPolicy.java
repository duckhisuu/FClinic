package com.fclinic.appointmentservice.config;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ProviderCredentialPolicy {
  public ProviderCredentialPolicy(ProviderCredentials providers, AppointmentSecuritySettings security) {
    Set<String> unique = new HashSet<>();
    for (String secret : new String[]{providers.doctorClientSecret(), providers.recipientClientSecret(),
        security.clientSecret(), security.scopeClientSecret(), security.patientClientSecret(),
        security.notificationClientSecret(), security.patientTokenClientSecret()})
      if (secret != null && !secret.isBlank() && !unique.add(secret))
        throw new IllegalArgumentException("Appointment service credentials must be distinct");
  }
}
