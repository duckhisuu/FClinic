package com.fclinic.appointmentservice.infrastructure.client.patient;

import com.fclinic.appointmentservice.infrastructure.client.config.*;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/** No @Configuration: credentials are scoped exclusively to this Feign child context. */
public class PatientFeignConfig extends FeignGlobalConfig {
  @Override protected boolean retryGet() { return true; }
  @Bean public ErrorDecoder errorDecoder() { return new PatientErrorDecoder(); }
  @Bean public RequestInterceptor authorization() {
    return template -> {
      template.header("Accept", "application/json");
      template.header("Accept-Encoding", "identity");
      template.header("Cache-Control", "no-store");
      var authorization = template.headers().get("Authorization");
      FeignHeaderGuards.require(authorization != null && authorization.size() == 1 && authorization.iterator().next().startsWith("Bearer "));
    };
  }
}

