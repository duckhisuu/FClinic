package com.fclinic.authservice.application.command;

public record OAuth2CodeCommand(
        String code,
        String redirectUri,
        String codeVerifier,
        String clientId
) {
    @Override
    public String toString() {
        return "OAuth2CodeCommand[redirectUri=" + redirectUri + ", clientId=" + clientId + "]";
    }
}

