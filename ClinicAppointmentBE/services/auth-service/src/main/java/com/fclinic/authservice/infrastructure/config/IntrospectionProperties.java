package com.fclinic.authservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.introspection")
public record IntrospectionProperties(
        String clientId,
        String clientSecret,
        String issuer,
        List<String> defaultAudiences,
        java.util.Map<String, Client> clients
) {
    public IntrospectionProperties { clients = clients == null ? java.util.Map.of() : java.util.Map.copyOf(clients); }
    public record Client(String clientId, String clientSecret) {
        @Override public String toString() { return "IntrospectionClient[redacted]"; }
    }
    @Override
    public String toString() {
        return "IntrospectionProperties[issuer=" + issuer + ", clientId=redacted, clientSecret=redacted]";
    }
}

