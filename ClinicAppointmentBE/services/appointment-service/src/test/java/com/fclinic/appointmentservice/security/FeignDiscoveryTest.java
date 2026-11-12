package com.fclinic.appointmentservice.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fclinic.appointmentservice.config.ProviderCredentials;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.infrastructure.client.config.FeignCalls;
import com.fclinic.appointmentservice.infrastructure.client.doctor.DoctorClient;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.openfeign.*;
import org.springframework.context.annotation.*;

class FeignDiscoveryTest {
  @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
  @EnableFeignClients(clients = DoctorClient.class)
  static class Clients {
    @Bean ProviderCredentials providerCredentials() { return new ProviderCredentials("doctor","doctor-secret","recipient","recipient-secret"); }
  }
  @Test void emptyBaseUrlResolvesServiceAndGuardsFinalInstanceUri() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    var requests = new AtomicInteger();
    server.createContext("/", ex -> {
      requests.incrementAndGet();
      assertThat(ex.getRequestHeaders().getFirst("Authorization"))
          .isEqualTo(com.fclinic.appointmentservice.infrastructure.client.config.FeignHeaderGuards.basic("doctor","doctor-secret"));
      ex.getResponseHeaders().set("X-FClinic-Reference-Contract","appointment-doctor-reference/1");
      ex.sendResponseHeaders(404,-1);ex.close();
    });
    server.start();
    var balancer = mock(LoadBalancerClient.class);
    var factory = mock(LoadBalancerClientFactory.class);
    when(factory.getProperties(anyString())).thenReturn(new LoadBalancerProperties());
    when(factory.getInstances(anyString(),eq(LoadBalancerLifecycle.class))).thenReturn(Map.of());
    when(balancer.choose(eq("doctor-service"),any())).thenReturn(new DefaultServiceInstance("doctor-1","doctor-service","127.0.0.1",server.getAddress().getPort(),false));
    when(balancer.reconstructURI(any(),any())).thenAnswer(i -> URI.create("http://127.0.0.1:"+server.getAddress().getPort()+((URI)i.getArgument(1)).getPath()));
    try {
      new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class))
          .withUserConfiguration(Clients.class).withPropertyValues("appointment.doctor-url=")
          .withBean(LoadBalancerClient.class,() -> balancer).withBean(LoadBalancerClientFactory.class,() -> factory)
          .run(context -> {
            assertThat(context).hasNotFailed();
            var doctor = context.getBean(DoctorClient.class);
            assertThatThrownBy(() -> FeignCalls.call(() -> doctor.getDoctor(UUID.randomUUID(),FeignCalls.options(Long.MAX_VALUE))))
                .isInstanceOfSatisfying(AppointmentError.class,e -> assertThat(e.code()).isEqualTo("DOCTOR_NOT_FOUND"));
            assertThat(requests.get()).isEqualTo(1);
            doReturn(URI.create("http://doctor.external.invalid/internal/v1/doctors/any")).when(balancer).reconstructURI(any(),any());
            assertThatThrownBy(() -> FeignCalls.call(() -> doctor.getDoctor(UUID.randomUUID(),FeignCalls.options(Long.MAX_VALUE))))
                .isInstanceOfSatisfying(AppointmentError.class,e -> assertThat(e.status()).isEqualTo(503));
            assertThat(requests.get()).isEqualTo(1);
          });
    } finally { server.stop(0); }
  }
}
