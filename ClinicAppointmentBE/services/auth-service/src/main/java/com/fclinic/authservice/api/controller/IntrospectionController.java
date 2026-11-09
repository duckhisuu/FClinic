package com.fclinic.authservice.api.controller;

import com.fclinic.authservice.api.mapper.AuthApiMapper;
import com.fclinic.authservice.application.model.IntrospectionResult;
import com.fclinic.authservice.application.port.in.IntrospectTokenUseCase;
import com.fclinic.authservice.infrastructure.config.IntrospectionProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

/**
 * RFC 7662 / patient-identity-introspection/2 compatible endpoint for backend service consumers.
 * Returns unwrapped JSON directly (not ApiResponse envelope) so Patient Service IntrospectionDecoder
 * validates active=false (1 field) and active=true (exact 11 fields with canonical UUIDv4 IDs).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class IntrospectionController {

    private final IntrospectTokenUseCase introspectTokenUseCase;
    private final IntrospectionProperties properties;
    private final AuthApiMapper mapper;

    public IntrospectionController(
            IntrospectTokenUseCase introspectTokenUseCase,
            IntrospectionProperties properties,
            AuthApiMapper mapper
    ) {
        this.introspectTokenUseCase = introspectTokenUseCase;
        this.properties = properties;
        this.mapper = mapper;
    }

    @PostMapping(
            value = "/introspect",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Object> introspect(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody(required = false) MultiValueMap<String, String> form
    ) {
        if (!isAuthorizedClient(authorizationHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (form == null) {
            return ResponseEntity.badRequest().build();
        }

        List<String> tokens = form.get("token");
        if (tokens == null || tokens.size() != 1 || tokens.get(0) == null || tokens.get(0).isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        List<String> hints = form.get("token_type_hint");
        if (hints != null && (hints.size() != 1 || !"access_token".equals(hints.get(0)))) {
            return ResponseEntity.badRequest().build();
        }

        IntrospectionResult result = introspectTokenUseCase.introspect(tokens.get(0));
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapper.toIntrospectionWireResponse(result));
    }

    private boolean isAuthorizedClient(String authorizationHeader) {
        return com.fclinic.authservice.infrastructure.config.ServiceClientCredentials.matches(
                authorizationHeader, properties.clientId(), properties.clientSecret())
                || properties.clients().entrySet().stream().anyMatch(entry ->
                    com.fclinic.authservice.infrastructure.config.ServiceClientCredentials.matches(
                        authorizationHeader, entry.getValue().clientId(), entry.getValue().clientSecret()));
    }
}
