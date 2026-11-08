package com.fclinic.authservice.application.model;

public record KeycloakPublicConfigResult(
        String realm,
        String authorizationEndpoint,
        String tokenEndpoint,
        String endSessionEndpoint,
        String jwkSetUri,
        String webClientId,
        String authClientId
) {
}

