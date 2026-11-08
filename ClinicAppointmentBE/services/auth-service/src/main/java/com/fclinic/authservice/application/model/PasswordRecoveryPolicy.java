package com.fclinic.authservice.application.model;

public record PasswordRecoveryPolicy(
        long expirationSeconds,
        boolean exposeOtpInResponse
) {
}

