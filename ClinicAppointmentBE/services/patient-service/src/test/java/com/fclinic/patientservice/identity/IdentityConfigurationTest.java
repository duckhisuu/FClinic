package com.fclinic.patientservice.identity;

import com.fclinic.patientservice.application.port.out.IdentityVerifierPort;
import com.fclinic.patientservice.application.security.BearerTokens;
import com.fclinic.patientservice.config.*;
import com.fclinic.patientservice.infrastructure.adapter.identity.*;
import com.fclinic.patientservice.domain.model.PatientError;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static com.fclinic.patientservice.identity.IdentityFixtures.*;
import static org.assertj.core.api.Assertions.*;

class IdentityConfigurationTest {
    final ApplicationContextRunner runner=new ApplicationContextRunner()
            .withUserConfiguration(PatientConfiguration.class,PatientIdentityConfiguration.class);
    @Test void disabledConfigurationHasExactlyOneFailClosedVerifier() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(IdentityVerifierPort.class);
            assertThatThrownBy(() -> context.getBean(IdentityVerifierPort.class).verify("fixture"))
                    .isInstanceOfSatisfying(PatientError.class,e -> assertThat(e.code()).isEqualTo(PatientError.Code.DEPENDENCY_UNAVAILABLE));
        });
    }
    @Test void enabledConfigurationSelectsExactlyOneProductionVerifier() {
        runner.withPropertyValues("patient.identity.enabled=true","patient.identity.endpoint=https://localhost/introspection",
                "patient.identity.issuer=https://identity.example.test","patient.identity.client-id=fixture",
                "patient.identity.client-secret=synthetic-test-value")
                .run(context -> { assertThat(context).hasSingleBean(IdentityVerifierPort.class);
                    assertThat(context.getBean(IdentityVerifierPort.class)).isInstanceOf(IntrospectionIdentityVerifier.class); });
    }
    @Test void missingOrInsecureEnabledConfigurationFailsStartupClosed() {
        for(String uri:new String[]{"","http://localhost/introspection","https://user@localhost/endpoint","https://localhost/endpoint?secret=x"}) {
            runner.withPropertyValues("patient.identity.enabled=true","patient.identity.endpoint="+uri)
                    .run(context -> assertThat(context).hasFailed());
        }
    }
    @Test void noConfigObjectToStringContainsSecrets() {
        var s=settings(); assertThat(s.toString()).doesNotContain(s.clientSecret(),s.clientId(),s.endpoint().toString());
    }
    @Test void strictBearerSyntaxRejectsAmbiguousHeaderValues() {
        for(String v:new String[]{"","Basic fixture","Bearer a b","Bearer a,b","Bearer a:secret","Bearer x\t",
                "Bearer =","Bearer x=y","Bearer  x","Bearer x\r\nX-Role: ADMIN","Bearer "+"a".repeat(8193)}) {
            assertThatThrownBy(() -> BearerTokens.fromAuthorization(v)).isInstanceOfSatisfying(PatientError.class,
                    e -> assertThat(e.code()).isEqualTo(PatientError.Code.UNAUTHENTICATED));
        }
        assertThat(BearerTokens.fromAuthorization("bEaReR ab_~+/-.==")).isEqualTo("ab_~+/-.==");
    }
}

