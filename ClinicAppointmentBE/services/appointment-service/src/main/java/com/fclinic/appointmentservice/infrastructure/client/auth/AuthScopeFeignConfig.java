package com.fclinic.appointmentservice.infrastructure.client.auth;

import com.fclinic.appointmentservice.infrastructure.client.config.*;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/** No @Configuration: credentials are scoped exclusively to this Feign child context. */
public class AuthScopeFeignConfig extends FeignGlobalConfig {
  @Bean public ErrorDecoder errorDecoder() { return new AuthScopeErrorDecoder(); }
  @Bean public RequestInterceptor authorization(com.fclinic.appointmentservice.config.AppointmentSecuritySettings security) {
    return template -> {
      template.header("Accept", "application/json");
      template.header("Accept-Encoding", "identity");
      template.header("Cache-Control", "no-store");
      template.header("Authorization", FeignHeaderGuards.basic(security.scopeClientId(), security.scopeClientSecret()));
    };
  }
}

