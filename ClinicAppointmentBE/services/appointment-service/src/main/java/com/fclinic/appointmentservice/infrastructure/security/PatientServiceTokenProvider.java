package com.fclinic.appointmentservice.infrastructure.security;

import static com.fclinic.appointmentservice.infrastructure.client.config.FeignHeaderGuards.require;
import com.fclinic.appointmentservice.config.*;
import com.fclinic.appointmentservice.infrastructure.client.keycloak.KeycloakTokenClient;
import com.fclinic.appointmentservice.infrastructure.client.config.FeignCalls;
import java.time.*;
import org.springframework.stereotype.Component;

/** Separate machine token; never forwards or caches the browser bearer. */
@Component
public class PatientServiceTokenProvider {
  private final KeycloakTokenClient client;
  private final AppointmentSecuritySettings security;
  private final AppointmentSettings settings;
  private final Clock clock;
  private String token;
  private Instant refreshAt = Instant.EPOCH;
  public PatientServiceTokenProvider(KeycloakTokenClient client, AppointmentSecuritySettings security,
      AppointmentSettings settings, Clock clock) { this.client = client; this.security = security; this.settings = settings; this.clock = clock; }
  public synchronized String token(long deadline) {
    FeignCalls.options(deadline);
    if (security.patientTokenUri() == null) return settings.serviceToken();
    if (token != null && clock.instant().isBefore(refreshAt)) return token;
    return FeignCalls.call(() -> {
      var result = client.token(security.patientTokenUri(), "grant_type=client_credentials", FeignCalls.options(deadline));
      require(result != null && result.access_token() != null && result.access_token().matches("[A-Za-z0-9._~+/-]{1,8192}={0,2}")
          && "Bearer".equalsIgnoreCase(result.token_type()) && result.expires_in() != null && result.expires_in() > 0);
      token = result.access_token();
      long ttl = Math.min(result.expires_in(), 3600);
      refreshAt = clock.instant().plusSeconds(Math.max(0, ttl - Math.min(30, ttl / 2)));
      return token;
    });
  }
}
