package com.fclinic.appointmentservice.security;

import com.fclinic.appointmentservice.infrastructure.client.config.FeignJacksonSupport;
import com.fclinic.appointmentservice.infrastructure.client.config.FeignHeaderGuards;

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
    var http = mock(com.fclinic.appointmentservice.infrastructure.client.keycloak.KeycloakTokenClient.class); var clock = mock(Clock.class);
    var now = Instant.parse("2026-11-09T00:00:00Z"); when(clock.instant()).thenReturn(now);
    var settings = new AppointmentSecuritySettings(null, "issuer", "appointment-service", "identity", "identity-secret",
        null, "scope", "scope-secret", "patient", "reference-secret", null, null,
        URI.create("https://keycloak.example.test/token"), "appointment-service", "machine-secret");
    when(http.token(any(), any(), any())).thenReturn(FeignJacksonSupport.JSON.readValue(
        "{\"access_token\":\"machine-token\",\"token_type\":\"Bearer\",\"expires_in\":60}", com.fclinic.appointmentservice.infrastructure.client.keycloak.dto.KeycloakTokenDto.class));
    var provider = new PatientServiceTokenProvider(http, settings, mock(AppointmentSettings.class), clock);
    assertThat(provider.token(Long.MAX_VALUE)).isEqualTo("machine-token");
    assertThat(provider.token(Long.MAX_VALUE)).isEqualTo("machine-token");
    verify(http, times(1)).token(eq(settings.patientTokenUri()), eq("grant_type=client_credentials"), any());
    when(clock.instant()).thenReturn(now.plusSeconds(31)); provider.token(Long.MAX_VALUE);
    verify(http, times(2)).token(any(), any(), any());
  }
  @Test void invalidOrFailedTokenExchangeDoesNotFallBackToHumanOrStaleToken() {
    var http = mock(com.fclinic.appointmentservice.infrastructure.client.keycloak.KeycloakTokenClient.class);
    var settings = new AppointmentSecuritySettings(null, null, null, null, null, null, null, null, null, null, null, null,
        URI.create("https://keycloak.example.test/token"), "machine", "machine-secret");
    when(http.token(any(), any(), any())).thenReturn(FeignJacksonSupport.JSON.readValue("{\"access_token\":\"bad\",\"expires_in\":0}", com.fclinic.appointmentservice.infrastructure.client.keycloak.dto.KeycloakTokenDto.class));
    var provider = new PatientServiceTokenProvider(http, settings, mock(AppointmentSettings.class), Clock.systemUTC());
    assertThatThrownBy(() -> provider.token(Long.MAX_VALUE)).isInstanceOfSatisfying(AppointmentError.class, e -> assertThat(e.status()).isEqualTo(503));
  }
  @Test void existingExplicitServiceCredentialIsSupportedWhenNoTokenEndpointIsSelected() {
    var http = mock(com.fclinic.appointmentservice.infrastructure.client.keycloak.KeycloakTokenClient.class); var settings = mock(AppointmentSettings.class);
    when(settings.serviceToken()).thenReturn("provisioned-machine-token");
    var provider = new PatientServiceTokenProvider(http, SecurityFixtures.settings(null), settings, Clock.systemUTC());
    assertThat(provider.token(Long.MAX_VALUE)).isEqualTo("provisioned-machine-token"); verifyNoInteractions(http);
  }
}
