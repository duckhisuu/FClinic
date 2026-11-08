package com.fclinic.authservice.api.dto.response;

public record KeycloakConfigResponse(
        String realm,
        String authorizationEndpoint,
        String tokenEndpoint,
        String endSessionEndpoint,
        String jwkSetUri,
        String webClientId
) {
}

