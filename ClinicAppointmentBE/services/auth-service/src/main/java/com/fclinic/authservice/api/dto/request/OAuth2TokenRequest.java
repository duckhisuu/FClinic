package com.fclinic.authservice.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OAuth2TokenRequest(
        @NotBlank(message = "Authorization code không được để trống")
        String code,

        @NotBlank(message = "Redirect URI không được để trống")
        String redirectUri,

        String codeVerifier,
        String clientId
) {
}

