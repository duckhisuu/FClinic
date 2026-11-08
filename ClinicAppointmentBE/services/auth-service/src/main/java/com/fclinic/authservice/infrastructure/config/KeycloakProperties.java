package com.fclinic.authservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.keycloak")
public record KeycloakProperties(
        String baseUrl,
        String realm,
        String clientId,
        String clientSecret,
        String webClientId,
        String adminRealm,
        String adminUsername,
        String adminPassword,
        int connectTimeoutMs,
        int readTimeoutMs
) {
    public String realmBaseUrl() {
        return stripTrailingSlash(baseUrl) + "/realms/" + realm;
    }

    public String adminRealmUsersUrl() {
        return stripTrailingSlash(baseUrl) + "/admin/realms/" + realm + "/users";
    }

    public String adminRealmRolesUrl() {
        return stripTrailingSlash(baseUrl) + "/admin/realms/" + realm + "/roles";
    }

    public String tokenEndpoint() {
        return realmBaseUrl() + "/protocol/openid-connect/token";
    }

    public String authorizationEndpoint() {
        return realmBaseUrl() + "/protocol/openid-connect/auth";
    }

    public String introspectionEndpoint() {
        return realmBaseUrl() + "/protocol/openid-connect/token/introspect";
    }

    public String logoutEndpoint() {
        return realmBaseUrl() + "/protocol/openid-connect/logout";
    }

    public String jwkSetUri() {
        return realmBaseUrl() + "/protocol/openid-connect/certs";
    }

    public String adminTokenEndpoint() {
        String targetRealm = (adminRealm == null || adminRealm.isBlank()) ? "master" : adminRealm;
        return stripTrailingSlash(baseUrl) + "/realms/" + targetRealm + "/protocol/openid-connect/token";
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public String toString() {
        return "KeycloakProperties[baseUrl=" + baseUrl + ", realm=" + realm + ", clientId=" + clientId + ", secrets=redacted]";
    }
}

