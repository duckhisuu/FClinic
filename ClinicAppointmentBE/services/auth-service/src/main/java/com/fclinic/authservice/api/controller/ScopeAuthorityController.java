package com.fclinic.authservice.api.controller;

import com.fclinic.authservice.application.service.ScopeAuthorityService;
import com.fclinic.authservice.infrastructure.config.ScopeAuthorityProperties;
import com.fclinic.authservice.infrastructure.config.ServiceClientCredentials;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Dedicated client credentials; no browser bearer, account role, or unsigned header grants access. */
@RestController
@RequestMapping("/api/v1/auth/scopes")
public class ScopeAuthorityController {
  private final ScopeAuthorityProperties settings;
  private final ScopeAuthorityService service;
  public ScopeAuthorityController(ScopeAuthorityProperties settings, ScopeAuthorityService service) {
    this.settings = settings; this.service = service;
  }
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> evaluate(@RequestHeader(value = "Authorization", required = false) String credential,
      @RequestBody ScopeAuthorityService.Query query) {
    boolean permitted = settings.clients().entrySet().stream().anyMatch(entry ->
        ServiceClientCredentials.matches(credential, entry.getValue().clientId(), entry.getValue().secret())
        && java.util.Objects.equals(entry.getValue().contractVersion(), query.contractVersion())
        && java.util.Objects.equals(entry.getValue().authority(), query.authority()));
    if (!permitted) return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).build();
    if (query.operation() == null || query.caller() == null || query.nonce() == null || query.requestedAt() == null)
      return ResponseEntity.badRequest().build();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.evaluate(query));
  }
}
