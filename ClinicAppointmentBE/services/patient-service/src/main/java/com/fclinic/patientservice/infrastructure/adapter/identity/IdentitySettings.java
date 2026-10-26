package com.fclinic.patientservice.infrastructure.adapter.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** Local PROJECT_DECIDED introspection v1 configuration; never print credentials. */
@ConfigurationProperties(value = "patient.identity", ignoreUnknownFields = false)
public record IdentitySettings(
        @DefaultValue("false") boolean enabled,
        URI endpoint, String issuer, @DefaultValue("patient-service") String audience,
        String clientId, String clientSecret,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("3s") Duration exchangeTimeout,
        @DefaultValue("65536") int maxResponseBytes,
        @DefaultValue("32") int maxConcurrentRequests,
        Path trustStore, String trustStorePassword,
        Map<String, ServiceBinding> services) {
    public record ServiceBinding(String name, Set<String> authorities) {
        public ServiceBinding { authorities = authorities == null ? Set.of() : Set.copyOf(authorities); }
    }
    public IdentitySettings { services = services == null ? Map.of() : Map.copyOf(services); }
    @Override public String toString() { return "IdentitySettings[redacted]"; }

    public void validateEnabled() {
        require(endpoint != null && "https".equalsIgnoreCase(endpoint.getScheme()) && endpoint.getHost() != null
                && endpoint.getUserInfo() == null && endpoint.getFragment() == null && endpoint.getQuery() == null,
                "HTTPS endpoint required");
        require(issuer != null && !issuer.isBlank() && issuer.length() <= 512, "issuer required");
        require(audience != null && audience.matches("[A-Za-z0-9._:/-]{1,128}"), "audience required");
        require(clientId != null && clientId.matches("[A-Za-z0-9._-]{1,128}"), "client ID required");
        require(clientSecret != null && !clientSecret.isBlank() && clientSecret.length() <= 4096
                && clientSecret.chars().noneMatch(Character::isISOControl), "client secret required");
        require(duration(connectTimeout) && duration(exchangeTimeout)
                && connectTimeout.compareTo(exchangeTimeout) <= 0, "invalid timeout bounds");
        require(maxResponseBytes >= 1024 && maxResponseBytes <= 262144, "invalid response limit");
        require(maxConcurrentRequests >= 1 && maxConcurrentRequests <= 256, "invalid concurrency limit");
        require((trustStore == null) == (trustStorePassword == null), "trust store/password must be paired");
        services.forEach((subject, binding) -> {
            require(subject != null && !subject.isBlank() && subject.length() <= 255
                    && subject.chars().noneMatch(Character::isISOControl), "invalid service subject");
            require(binding != null && binding.name() != null && binding.name().matches("[a-z][a-z0-9-]{0,63}"),
                    "invalid service name");
            require(!binding.authorities().isEmpty() && binding.authorities().stream()
                    .allMatch(a -> a.matches("SVC_[A-Z0-9_]{1,80}")), "invalid service authority registry");
        });
    }
    private static boolean duration(Duration d) {
        return d != null && d.compareTo(Duration.ofMillis(100)) >= 0 && d.compareTo(Duration.ofSeconds(30)) <= 0;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Invalid patient identity configuration: " + message);
    }
}

