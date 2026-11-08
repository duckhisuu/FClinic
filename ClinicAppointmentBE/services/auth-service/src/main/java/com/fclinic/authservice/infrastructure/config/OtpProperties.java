package com.fclinic.authservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.otp")
public record OtpProperties(
        long expirationSeconds,
        boolean exposeInResponse,
        boolean mailEnabled,
        String fromAddress
) {
}

