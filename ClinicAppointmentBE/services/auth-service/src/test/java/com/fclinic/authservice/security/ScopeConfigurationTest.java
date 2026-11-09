package com.fclinic.authservice.security;

import static org.assertj.core.api.Assertions.*;
import com.fclinic.authservice.infrastructure.config.*;
import com.fclinic.authservice.infrastructure.adapter.ConfiguredScopeAssignments;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ScopeConfigurationTest {
  @Configuration(proxyBeanMethods=false)
  @EnableConfigurationProperties({ScopeAuthorityProperties.class, IntrospectionProperties.class})
  static class Settings { }
  @Test void actualYamlBindsRegistryGrantAndDistinctIntrospectionClient() {
    new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer()).withUserConfiguration(Settings.class)
        .withPropertyValues("spring.config.location=classpath:application.yml", "APPOINTMENT_AUTH_CLIENT_SECRET=synthetic-identity",
            "APPOINTMENT_SCOPE_CLIENT_SECRET=synthetic-scope")
        .run(c -> {
          assertThat(c).hasNotFailed();
          var identity = c.getBean(IntrospectionProperties.class);
          assertThat(identity.clients().get("appointment").clientId()).isEqualTo("appointment-introspection");
          var assignments = new ConfiguredScopeAssignments(c.getBean(ScopeAuthorityProperties.class));
          assertThat(assignments.services().get("appointment").authorities()).containsExactly("SVC_PATIENT_READ");
          assertThat(assignments.grants().values()).singleElement().satisfies(grant -> {
            assertThat(grant.operations()).containsExactly("APPOINTMENT_PROFILE_READ"); assertThat(grant.allPatients()).isTrue();
            assertThat(grant.validFrom()).isNotNull(); assertThat(grant.validUntil()).isNotNull();
          });
        });
  }
}
