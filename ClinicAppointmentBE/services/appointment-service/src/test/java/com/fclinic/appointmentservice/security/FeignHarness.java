package com.fclinic.appointmentservice.security;

import com.fclinic.appointmentservice.config.*;
import com.fclinic.appointmentservice.infrastructure.client.auth.*;
import com.fclinic.appointmentservice.infrastructure.client.config.*;
import com.fclinic.appointmentservice.infrastructure.client.doctor.*;
import com.fclinic.appointmentservice.infrastructure.client.patient.*;
import com.fclinic.appointmentservice.infrastructure.client.keycloak.*;
import feign.*;
import feign.codec.ErrorDecoder;
import java.util.*;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.mock.env.MockEnvironment;

/** TCP tests use the same contracts, guards, credentials, decoder and retry policy as Spring's proxies. */
public final class FeignHarness implements AutoCloseable {
  private final List<GuardedFeignClient> clients = new ArrayList<>();
  private final org.springframework.core.env.Environment environment;
  public FeignHarness() { this(new MockEnvironment()); }
  public FeignHarness(org.springframework.core.env.Environment environment) { this.environment = environment; }
  public <T> T bind(Class<T> type, String url, FeignGlobalConfig config, RequestInterceptor interceptor, ErrorDecoder errors) {
    var client = config.guardedClient(environment); clients.add(client);
    return Feign.builder().contract(new SpringMvcContract()).client(client).encoder(config.encoder())
        .decoder(config.decoder()).errorDecoder(errors).retryer(config.retryer()).requestInterceptor(interceptor)
        .target(type, url);
  }
  public PatientClient patient(String url) {
    var c = new PatientFeignConfig(); return bind(PatientClient.class, url, c, c.authorization(), c.errorDecoder());
  }
  public DoctorClient doctor(String url, ProviderCredentials credentials) {
    var c = new DoctorFeignConfig(); return bind(DoctorClient.class, url, c, c.authorization(credentials), c.errorDecoder());
  }
  public AuthRecipientClient recipients(ProviderCredentials credentials) {
    var c = new AuthRecipientFeignConfig(); return bind(AuthRecipientClient.class, "http://localhost", c, c.authorization(credentials), c.errorDecoder());
  }
  public AuthIntrospectClient introspect(AppointmentSecuritySettings security) {
    var c = new AuthIntrospectFeignConfig(); return bind(AuthIntrospectClient.class, "http://localhost", c, c.authorization(security), c.errorDecoder());
  }
  public AuthScopeClient scope(AppointmentSecuritySettings security) {
    var c = new AuthScopeFeignConfig(); return bind(AuthScopeClient.class, "http://localhost", c, c.authorization(security), c.errorDecoder());
  }
  public KeycloakTokenClient token(AppointmentSecuritySettings security) {
    var c = new KeycloakFeignConfig(); return bind(KeycloakTokenClient.class, "http://localhost", c, c.authorization(security), c.errorDecoder());
  }
  public void close() { clients.forEach(GuardedFeignClient::close); }
}
