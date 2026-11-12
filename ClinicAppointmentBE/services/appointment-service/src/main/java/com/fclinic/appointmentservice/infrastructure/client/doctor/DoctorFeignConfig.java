package com.fclinic.appointmentservice.infrastructure.client.doctor;

import com.fclinic.appointmentservice.infrastructure.client.config.*;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/** No @Configuration: credentials are scoped exclusively to this Feign child context. */
public class DoctorFeignConfig extends FeignGlobalConfig {
  @Override protected String contract() { return "appointment-doctor-reference/1"; }
  @Override protected boolean retryGet() { return true; }
  @Bean public ErrorDecoder errorDecoder() { return new DoctorErrorDecoder(); }
  @Bean public RequestInterceptor authorization(com.fclinic.appointmentservice.config.ProviderCredentials credentials) {
    return template -> {
      template.header("Accept", "application/json");
      template.header("Accept-Encoding", "identity");
      template.header("Cache-Control", "no-store");
      template.header("Authorization", FeignHeaderGuards.basic(credentials.doctorClientId(), credentials.doctorClientSecret()));
    };
  }
}

