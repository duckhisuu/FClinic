package com.fclinic.notificationservice.config;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class NotificationSecurityConfiguration {
    @Bean
    JwtDecoder notificationJwtDecoder(
            @Value("${notification.security.issuer}") String issuer,
            @Value("${notification.security.jwk-set-uri}") String jwks,
            @Value("${notification.security.audience}") String audience) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), identityValidator(audience)));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> identityValidator(String audience) {
        return jwt -> {
            try {
                String id = jwt.getClaimAsString("fclinic_user_id");
                UUID userId = UUID.fromString(id);
                List<String> authorities = jwt.getClaimAsStringList("authorities");
                if (userId.version() == 4 && userId.toString().equals(id)
                        && jwt.getSubject() != null && jwt.getAudience().contains(audience)
                        && authorities != null && authorities.stream().anyMatch(
                                List.of("ROLE_PATIENT", "ROLE_DOCTOR", "ROLE_STAFF", "ROLE_ADMIN")::contains)) {
                    return OAuth2TokenValidatorResult.success();
                }
            } catch (RuntimeException ignored) {
                // Missing or malformed identity claims fail closed.
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
    }

    @Bean
    SecurityFilterChain notificationSecurity(HttpSecurity http) throws Exception {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("authorities");
        authorities.setAuthorityPrefix("");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/admin/notifications", "/api/v1/admin/notifications/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/v1/notifications/me", "/api/v1/notifications/*").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }
}
