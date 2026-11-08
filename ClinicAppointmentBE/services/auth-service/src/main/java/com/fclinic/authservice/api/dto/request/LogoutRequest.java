package com.fclinic.authservice.api.dto.request;

public record LogoutRequest(
        String refreshToken
) {
    @Override
    public String toString() {
        return "LogoutRequest[redacted]";
    }
}

