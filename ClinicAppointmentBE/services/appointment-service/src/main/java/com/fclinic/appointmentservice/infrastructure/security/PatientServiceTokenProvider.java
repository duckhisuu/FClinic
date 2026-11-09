package com.fclinic.appointmentservice.infrastructure.security;

import com.fclinic.appointmentservice.config.*;
import com.fclinic.appointmentservice.domain.AppointmentError;
import java.time.*;
import org.springframework.stereotype.Component;

/** Separate machine token; never forwards or caches the browser bearer. */
@Component
public class PatientServiceTokenProvider {
  private final AuthHttpTransport http;
  private final AppointmentSecuritySettings security;
  private final AppointmentSettings settings;
  private final Clock clock;
  private String token;
  private Instant refreshAt = Instant.EPOCH;
  public PatientServiceTokenProvider(AuthHttpTransport http, AppointmentSecuritySettings security,
      AppointmentSettings settings, Clock clock) { this.http = http; this.security = security; this.settings = settings; this.clock = clock; }
  public synchronized String token(long deadline) {
    if (security.patientTokenUri() == null) return settings.serviceToken();
    if (token != null && clock.instant().isBefore(refreshAt)) return token;
    var result = http.post(security.patientTokenUri(), security.patientTokenClientId(), security.patientTokenClientSecret(),
        "application/x-www-form-urlencoded", "grant_type=client_credentials", deadline);
    if (!result.path("access_token").isString() || !result.path("access_token").asText().matches("[A-Za-z0-9._~+/-]{1,8192}={0,2}")
        || !"Bearer".equalsIgnoreCase(result.path("token_type").asText()) || !result.path("expires_in").isIntegralNumber()
        || !result.path("expires_in").canConvertToLong() || result.path("expires_in").longValue() <= 0)
      throw AppointmentError.unavailable();
    token = result.path("access_token").asText();
    long ttl = Math.min(result.path("expires_in").longValue(), 3600);
    refreshAt = clock.instant().plusSeconds(Math.max(0, ttl - Math.min(30, ttl / 2)));
    return token;
  }
}
