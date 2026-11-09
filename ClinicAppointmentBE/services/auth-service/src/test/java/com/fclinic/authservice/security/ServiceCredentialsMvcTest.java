package com.fclinic.authservice.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fclinic.authservice.api.controller.*;
import com.fclinic.authservice.api.mapper.AuthApiMapper;
import com.fclinic.authservice.application.model.IntrospectionResult;
import com.fclinic.authservice.application.port.in.IntrospectTokenUseCase;
import com.fclinic.authservice.application.service.ScopeAuthorityService;
import com.fclinic.authservice.infrastructure.config.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ServiceCredentialsMvcTest {
  String basic(String id, String secret) { return "Basic " + Base64.getEncoder().encodeToString((id + ":" + secret).getBytes(StandardCharsets.UTF_8)); }
  @Test void introspectionAcceptsDistinctClientsAndRejectsWrongCredential() throws Exception {
    var properties = new IntrospectionProperties("patient-service", "patient-secret", "https://identity.test", List.of("patient-service"),
        Map.of("appointment", new IntrospectionProperties.Client("appointment-introspection", "appointment-secret")));
    var useCase = mock(IntrospectTokenUseCase.class); when(useCase.introspect("candidate")).thenReturn(IntrospectionResult.inactive());
    var mvc = MockMvcBuilders.standaloneSetup(new IntrospectionController(useCase, properties, new AuthApiMapper())).build();
    for (String credential : List.of(basic("patient-service", "patient-secret"), basic("appointment-introspection", "appointment-secret")))
      mvc.perform(post("/api/v1/auth/introspect").header("Authorization", credential)
          .contentType("application/x-www-form-urlencoded").content("token=candidate&token_type_hint=access_token"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    mvc.perform(post("/api/v1/auth/introspect").header("Authorization", basic("appointment-introspection", "patient-secret"))
        .contentType("application/x-www-form-urlencoded").content("token=candidate")).andExpect(status().isUnauthorized());
    verify(useCase, times(2)).introspect("candidate");
    assertThat(properties.toString()).doesNotContain("patient-secret", "appointment-secret");
  }
  @Test void scopeCredentialIsBoundToContractAndAuthority() throws Exception {
    var properties = new ScopeAuthorityProperties(Map.of("appointment", new ScopeAuthorityProperties.Client("appointment-scope", "scope-secret", "appointment-work-scope/1", "ADMINISTRATIVE")), Map.of(), Map.of());
    var service = mock(ScopeAuthorityService.class); when(service.evaluate(any())).thenReturn(Map.of("allowed", false));
    var mvc = MockMvcBuilders.standaloneSetup(new ScopeAuthorityController(properties, service)).build();
    String body = "{\"contractVersion\":\"appointment-work-scope/1\",\"nonce\":\"" + UUID.randomUUID() + "\",\"requestedAt\":\"2026-11-09T00:00:00Z\",\"authority\":\"ADMINISTRATIVE\",\"operation\":\"APPOINTMENT_READ\",\"caller\":{\"subject\":\"synthetic\",\"principalType\":\"USER\",\"patientId\":null,\"doctorId\":null,\"serviceName\":null},\"patientId\":null,\"appointmentId\":null,\"visitDate\":null}";
    mvc.perform(post("/api/v1/auth/scopes").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/auth/scopes").header("Authorization", basic("appointment-scope", "scope-secret"))
        .contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(post("/api/v1/auth/scopes").header("Authorization", basic("appointment-scope", "scope-secret"))
        .contentType("application/json").content(body.replace("ADMINISTRATIVE", "CLINICAL"))).andExpect(status().isUnauthorized());
    verify(service, times(1)).evaluate(any());
  }
}
