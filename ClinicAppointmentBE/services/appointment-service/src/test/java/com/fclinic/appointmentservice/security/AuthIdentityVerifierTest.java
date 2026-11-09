package com.fclinic.appointmentservice.security;

import static com.fclinic.appointmentservice.security.SecurityFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.infrastructure.security.*;
import org.junit.jupiter.api.*;
import java.net.URI;
import tools.jackson.databind.node.ObjectNode;

class AuthIdentityVerifierTest {
  final AuthHttpTransport http = mock(AuthHttpTransport.class);
  final AuthIdentityVerifier verifier = new AuthIdentityVerifier(http, settings(URI.create("https://auth.example.test/introspect")), CLOCK);
  ObjectNode valid() {
    return (ObjectNode) AuthHttpTransport.JSON.valueToTree(new java.util.LinkedHashMap<String,Object>() {{
      put("active", true); put("contractVersion", "patient-identity-introspection/2"); put("iss", "https://identity.example.test");
      put("aud", java.util.List.of("appointment-service")); put("exp", CLOCK.instant().plusSeconds(60).getEpochSecond());
      put("sub", "verified-subject"); put("principalType", "USER"); put("userId", java.util.UUID.randomUUID());
      put("patientId", PATIENT); put("doctorId", null); put("authorities", java.util.List.of("ROLE_PATIENT"));
    }});
  }
  void answer(tools.jackson.databind.JsonNode body) { when(http.post(any(), any(), any(), any(), any(), anyLong())).thenReturn(body); }
  void status(int status) { assertThatThrownBy(() -> verifier.verify("Bearer candidate", Long.MAX_VALUE))
      .isInstanceOfSatisfying(AppointmentError.class, e -> assertThat(e.status()).isEqualTo(status)); }
  @Test void returnsOnlyVerifiedMappings() { answer(valid()); assertThat(verifier.verify("Bearer candidate", Long.MAX_VALUE).patientId()).isEqualTo(PATIENT); }
  @Test void inactiveAndExpiredAre401() {
    answer(AuthHttpTransport.JSON.readTree("{\"active\":false}")); status(401);
    answer(valid().put("exp", CLOCK.instant().getEpochSecond())); status(401);
  }
  @Test void issuerAndAudienceAreChecked() {
    answer(valid().put("iss", "https://wrong.example.test")); status(401);
    var n = valid(); n.putArray("aud").add("patient-service"); answer(n); status(401);
  }
  @Test void missingAndAmbiguousBearerNeverCallsAuth() {
    for (String header : new String[]{null, "Bearer a b", "Bearer a,b", "Basic credential", "Bearer  a"})
      assertThatThrownBy(() -> verifier.verify(header, Long.MAX_VALUE)).isInstanceOfSatisfying(AppointmentError.class, e -> assertThat(e.status()).isEqualTo(401));
    verifyNoInteractions(http);
  }
  @Test void serviceBearerCannotUseHumanApi() { answer(valid().put("principalType", "SERVICE")); status(403); }
  @Test void malformedAndProviderFailureAre503() {
    answer(valid().put("patientId", "1")); status(503);
    answer(valid().put("unexpected", true)); status(503);
    when(http.post(any(), any(), any(), any(), any(), anyLong())).thenThrow(AppointmentError.unavailable()); status(503);
  }
}
