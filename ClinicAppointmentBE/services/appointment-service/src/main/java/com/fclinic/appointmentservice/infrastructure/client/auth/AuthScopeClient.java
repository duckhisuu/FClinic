package com.fclinic.appointmentservice.infrastructure.client.auth;

import com.fclinic.appointmentservice.infrastructure.client.auth.dto.*;
import feign.Request;
import java.net.URI;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "auth-service", contextId = "appointmentScope", url = "http://localhost", configuration = AuthScopeFeignConfig.class)
public interface AuthScopeClient {
  @PostMapping(consumes = "application/json")
  ScopeDecisionDto evaluate(URI endpoint, @RequestBody ScopeQueryDto request, Request.Options options);
}

