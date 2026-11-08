package com.fclinic.authservice.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token không được để trống")
        String refreshToken
) {
    @Override
    public String toString() {
        return "RefreshTokenRequest[redacted]";
    }
}

