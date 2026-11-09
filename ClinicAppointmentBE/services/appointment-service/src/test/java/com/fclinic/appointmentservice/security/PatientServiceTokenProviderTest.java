package com.fclinic.appointmentservice.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fclinic.appointmentservice.config.*;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.infrastructure.security.*;
import java.net.URI;
import java.time.*;
import org.junit.jupiter.api.Test;

class PatientServiceTokenProviderTest {
  @Test void fetchesSeparateMachineTokenCachesAndRefreshesBeforeExpiry() {
    var http = mock(AuthHttpTransport.class); var clock = mock(Clock.class);
    var now = Instant.parse("2026-11-09T00:00:00Z"); when(clock.instant()).thenReturn(now);
    var settings = new AppointmentSecuritySettings(null, "issuer", "appointment-service", "identity", "identity-secret",
        null, "scope", "scope-secret", "patient", "reference-secret", null, null,
        URI.create("https://keycloak.example.test/token"), "appointment-service", "machine-secret");
    when(http.post(any(), any(), any(), any(), any(), anyLong())).thenReturn(AuthHttpTransport.JSON.readTree(
        "{\"access_token\":\"machine-token\",\"token_type\":\"Bearer\",\"expires_in\":60}"));
    var provider = new PatientServiceTokenProvider(http, settings, mock(AppointmentSettings.class), clock);
    assertThat(provider.token(Long.MAX_VALUE)).isEqualTo("machine-token");
    assertThat(provider.token(Long.MAX_VALUE)).isEqualTo("machine-token");
    verify(http, times(1)).post(settings.patientTokenUri(), "appointment-service", "machine-secret",
        "application/x-www-form-urlencoded", "grant_type=client_credentials", Long.MAX_VALUE);
    when(clock.instant()).thenReturn(now.plusSeconds(31)); provider.token(Long.MAX_VALUE);
    verify(http, times(2)).post(any(), any(), any(), any(), any(), anyLong());
  }
  @Test void invalidOrFailedTokenExchangeDoesNotFallBackToHumanOrStaleToken() {
    var http = mock(AuthHttpTransport.class);
    var settings = new AppointmentSecuritySettings(null, null, null, null, null, null, null, null, null, null, null, null,
        URI.create("https://keycloak.example.test/token"), "machine", "machine-secret");
    when(http.post(any(), any(), any(), any(), any(), anyLong())).thenReturn(AuthHttpTransport.JSON.readTree("{\"access_token\":\"bad\",\"expires_in\":0}"));
    var provider = new PatientServiceTokenProvider(http, settings, mock(AppointmentSettings.class), Clock.systemUTC());
    assertThatThrownBy(() -> provider.token(Long.MAX_VALUE)).isInstanceOfSatisfying(AppointmentError.class, e -> assertThat(e.status()).isEqualTo(503));
  }
  @Test void existingExplicitServiceCredentialIsSupportedWhenNoTokenEndpointIsSelected() {
    var http = mock(AuthHttpTransport.class); var settings = mock(AppointmentSettings.class);
    when(settings.serviceToken()).thenReturn("provisioned-machine-token");
    var provider = new PatientServiceTokenProvider(http, SecurityFixtures.settings(null), settings, Clock.systemUTC());
    assertThat(provider.token(Long.MAX_VALUE)).isEqualTo("provisioned-machine-token"); verifyNoInteractions(http);
  }
}
