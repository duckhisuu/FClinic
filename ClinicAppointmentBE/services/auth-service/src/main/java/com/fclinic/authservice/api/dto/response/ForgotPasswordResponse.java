package com.fclinic.authservice.api.dto.response;

public record ForgotPasswordResponse(
        String email,
        String message,
        String otp,
        long expiresInSeconds
) {
    @Override
    public String toString() {
        return "ForgotPasswordResponse[email=" + email + ", expiresInSeconds=" + expiresInSeconds + "]";
    }
}

