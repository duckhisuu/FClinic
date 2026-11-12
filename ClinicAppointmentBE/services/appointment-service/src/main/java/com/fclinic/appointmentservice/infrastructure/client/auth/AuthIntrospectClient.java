package com.fclinic.appointmentservice.infrastructure.client.auth;

import com.fclinic.appointmentservice.infrastructure.client.auth.dto.IntrospectionResponseDto;
import feign.Request;
import java.net.URI;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "auth-service", contextId = "appointmentIntrospection", url = "http://localhost", configuration = AuthIntrospectFeignConfig.class)
public interface AuthIntrospectClient {
  @PostMapping(consumes = "application/x-www-form-urlencoded")
  IntrospectionResponseDto introspect(URI endpoint, @RequestBody String form, Request.Options options);
}

