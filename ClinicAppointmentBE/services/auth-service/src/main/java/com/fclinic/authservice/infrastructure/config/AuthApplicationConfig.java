package com.fclinic.authservice.infrastructure.config;

import com.fclinic.authservice.application.model.IntrospectionPolicy;
import com.fclinic.authservice.application.model.PasswordRecoveryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

@Configuration
public class AuthApplicationConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public IntrospectionPolicy introspectionPolicy(IntrospectionProperties introspectionProperties, KeycloakProperties keycloakProperties) {
        String issuer = (introspectionProperties.issuer() != null && !introspectionProperties.issuer().isBlank())
                ? introspectionProperties.issuer()
                : keycloakProperties.realmBaseUrl();
        List<String> audiences = (introspectionProperties.defaultAudiences() != null && !introspectionProperties.defaultAudiences().isEmpty())
                ? introspectionProperties.defaultAudiences()
                : List.of("patient-service", "auth-service");
        return new IntrospectionPolicy(issuer, audiences);
    }

    @Bean
    public PasswordRecoveryPolicy passwordRecoveryPolicy(OtpProperties otpProperties) {
        long ttl = otpProperties.expirationSeconds() > 0 ? otpProperties.expirationSeconds() : 300L;
        return new PasswordRecoveryPolicy(ttl, otpProperties.exposeInResponse());
    }
}

