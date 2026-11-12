package com.fclinic.appointmentservice.infrastructure.client.auth;

import com.fclinic.appointmentservice.infrastructure.client.config.ProviderResponse;
import com.fclinic.appointmentservice.infrastructure.client.auth.dto.*;
import feign.Request;
import java.net.URI;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "auth-service", contextId = "appointmentRecipient", url = "http://localhost", configuration = AuthRecipientFeignConfig.class)
public interface AuthRecipientClient {
  @PostMapping(consumes = "application/json")
  ResponseEntity<ProviderResponse<RecipientAccountsDto>> accounts(URI endpoint, @RequestBody RecipientLookupRequestDto request, Request.Options options);
}

