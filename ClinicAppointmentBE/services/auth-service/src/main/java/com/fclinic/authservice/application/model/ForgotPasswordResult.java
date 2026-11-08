package com.fclinic.authservice.application.model;

public record ForgotPasswordResult(
        String email,
        String message,
        String otp,
        long expiresInSeconds
) {
    @Override
    public String toString() {
        return "ForgotPasswordResult[email=" + email + ", expiresInSeconds=" + expiresInSeconds + "]";
    }
}

