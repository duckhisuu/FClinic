package com.fclinic.patientservice.config;

import com.fclinic.patientservice.infrastructure.adapter.identity.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentitySettings.class)
public class PatientIdentityConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "patient.identity.enabled", havingValue = "true")
    IntrospectionIdentityVerifier introspectionIdentityVerifier(IdentitySettings settings, Clock clock) {
        return new IntrospectionIdentityVerifier(settings, clock);
    }
}

