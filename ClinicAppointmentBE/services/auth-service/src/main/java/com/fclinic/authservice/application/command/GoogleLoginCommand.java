package com.fclinic.authservice.application.command;

public record GoogleLoginCommand(
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
        return "GoogleLoginCommand[email=" + email + ", hasAuthCode=" + (authorizationCode != null && !authorizationCode.isBlank()) + "]";
    }
}

