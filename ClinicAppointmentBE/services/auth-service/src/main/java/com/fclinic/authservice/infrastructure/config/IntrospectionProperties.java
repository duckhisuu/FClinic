package com.fclinic.authservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.introspection")
public record IntrospectionProperties(
        String clientId,
        String clientSecret,
        String issuer,
        List<String> defaultAudiences
) {
    @Override
    public String toString() {
        return "IntrospectionProperties[issuer=" + issuer + ", clientId=redacted, clientSecret=redacted]";
    }
}

