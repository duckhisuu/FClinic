package com.fclinic.appointmentservice.infrastructure.client.keycloak.dto;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakTokenDto(String access_token, String token_type, Long expires_in) {}

