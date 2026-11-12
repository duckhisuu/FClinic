package com.fclinic.appointmentservice.infrastructure.client.keycloak;

import com.fclinic.appointmentservice.infrastructure.client.keycloak.dto.KeycloakTokenDto;
import feign.Request;
import java.net.URI;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "keycloak", contextId = "appointmentMachineToken", url = "http://localhost", configuration = KeycloakFeignConfig.class)
public interface KeycloakTokenClient {
  @PostMapping(consumes = "application/x-www-form-urlencoded")
  KeycloakTokenDto token(URI endpoint, @RequestBody String form, Request.Options options);
}

