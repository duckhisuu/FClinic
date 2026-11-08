package com.fclinic.authservice.api.dto.request;

public record GoogleLoginRequest(
        String email,
        String name,
        String googleId,
        String avatarUrl,
        String idToken,
        String authorizationCode,
        String redirectUri,
        String codeVerifier
) {
    @Override
    public String toString() {
        return "GoogleLoginRequest[email=" + email + "]";
    }
}

