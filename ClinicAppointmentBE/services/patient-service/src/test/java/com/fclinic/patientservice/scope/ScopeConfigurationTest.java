package com.fclinic.patientservice.scope;

import com.fclinic.patientservice.application.port.out.WorkScopePort;
import com.fclinic.patientservice.config.*;
import com.fclinic.patientservice.infrastructure.adapter.scope.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static com.fclinic.patientservice.scope.ScopeFixtures.*;
import static org.assertj.core.api.Assertions.*;

class ScopeConfigurationTest {
    final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(
            PatientConfiguration.class,PatientIdentityConfiguration.class,PatientScopeConfiguration.class);
    final String[] valid={"patient.scope.enabled=true","patient.scope.administrative.endpoint=https://localhost/admin",
            "patient.scope.administrative.client-id=admin-fixture","patient.scope.administrative.client-secret=admin-test-only",
            "patient.scope.clinical.endpoint=https://localhost/clinical",
            "patient.scope.clinical.client-id=clinical-fixture","patient.scope.clinical.client-secret=clinical-test-only"};
    @Test void disabledHasOneDenyFallback() {
        runner.run(c -> { assertThat(c).hasSingleBean(WorkScopePort.class);
            assertThat(c.getBean(WorkScopePort.class).clinical(DOCTOR,PATIENT)).isFalse(); });
    }
    @Test void enabledSelectsExactlyOneProductionBean() {
        runner.withPropertyValues(valid).run(c -> { assertThat(c).hasSingleBean(WorkScopePort.class);
            assertThat(c.getBean(WorkScopePort.class)).isInstanceOf(RemoteWorkScopeAdapter.class); });
    }
    @Test void missingOrInsecureSettingsFailStartupClosed() {
        runner.withPropertyValues("patient.scope.enabled=true").run(c -> assertThat(c).hasFailed());
        runner.withPropertyValues(valid).withPropertyValues("patient.scope.clinical.endpoint=http://localhost/clinical")
                .run(c -> assertThat(c).hasFailed());
    }
    @Test void credentialsMustBeSeparateFromIdentityAndEachOther() {
        runner.withPropertyValues(valid).withPropertyValues("patient.scope.clinical.client-secret=admin-test-only")
                .run(c -> assertThat(c).hasFailed());
        runner.withPropertyValues(valid).withPropertyValues("patient.identity.client-id=admin-fixture")
                .run(c -> assertThat(c).hasFailed());
    }
    @Test void settingsStringsNeverExposeCredentialOrUrl() {
        try(var server=new TestScopeServer()) {
            var s=server.settings(java.time.Duration.ofSeconds(3));
            assertThat(s.toString()).doesNotContain(server.adminSecret,server.clinicalSecret,"localhost");
            assertThat(s.administrative().toString()).doesNotContain(server.adminSecret,"localhost");
        }
    }
}

