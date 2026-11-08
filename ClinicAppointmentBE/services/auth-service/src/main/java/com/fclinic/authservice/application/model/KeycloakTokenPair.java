package com.fclinic.authservice.application.model;

public record KeycloakTokenPair(
        String accessToken,
        String refreshToken,
        String idToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        String scope
) {
    @Override
    public String toString() {
        return "KeycloakTokenPair[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", tokens=redacted]";
    }
}

