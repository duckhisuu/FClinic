package com.fclinic.patientservice.reference;

import com.fclinic.patientservice.config.*;
import com.fclinic.patientservice.application.port.out.*;
import com.fclinic.patientservice.infrastructure.adapter.reference.*;
import com.fclinic.patientservice.domain.model.PatientError;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class ReferenceConfigurationTest {
    final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(PatientConfiguration.class,
            PatientIdentityConfiguration.class,PatientScopeConfiguration.class,PatientReferenceConfiguration.class);
    final String[] doctor={"patient.reference.doctor.enabled=true","patient.reference.doctor.collection-uri=https://localhost/api/v1/doctors",
            "patient.reference.doctor.client-id=doctor-test-client","patient.reference.doctor.client-secret=doctor-test-secret"};
    final String[] appointment={"patient.reference.appointment.enabled=true","patient.reference.appointment.collection-uri=https://localhost/internal/v1/appointments",
            "patient.reference.appointment.client-id=appointment-test-client","patient.reference.appointment.client-secret=appointment-test-secret"};
    @Test void bothPortsSelectExactlyOneIndependentProductionBean() {
        runner.withPropertyValues(doctor).withPropertyValues(appointment).run(c -> {
            assertThat(c).hasSingleBean(DoctorClientPort.class).hasSingleBean(AppointmentClientPort.class);
            assertThat(c.getBean(AppointmentClientPort.class)).isInstanceOf(RemoteAppointmentClientAdapter.class);
        });
    }
    @Test void appointmentEnabledWithoutSettingsFailsClosed() {
        runner.withPropertyValues("patient.reference.appointment.enabled=true").run(c -> assertThat(c).hasFailed());
        runner.withPropertyValues(appointment).withPropertyValues("patient.identity.client-secret=appointment-test-secret")
                .run(c -> assertThat(c).hasFailed());
    }
    @Test void disabledPortsHaveExactlyOneFailClosedFallback() {
        runner.run(c -> {
            assertThat(c).hasSingleBean(DoctorClientPort.class).hasSingleBean(AppointmentClientPort.class);
            assertThatThrownBy(() -> c.getBean(DoctorClientPort.class).find(12)).isInstanceOf(PatientError.class);
            assertThatThrownBy(() -> c.getBean(AppointmentClientPort.class).find(102)).isInstanceOf(PatientError.class);
        });
    }
    @Test void enabledDoctorSelectsExactlyOneProductionBean() {
        runner.withPropertyValues(doctor).run(c -> {
            assertThat(c).hasSingleBean(DoctorClientPort.class);
            assertThat(c.getBean(DoctorClientPort.class)).isInstanceOf(RemoteDoctorClientAdapter.class);
        });
    }
    @Test void invalidEnabledSettingsFailStartupClosed() {
        runner.withPropertyValues("patient.reference.doctor.enabled=true").run(c -> assertThat(c).hasFailed());
        for(String uri:new String[]{"http://localhost/doctors","https://localhost/doctors/","https://user@localhost/doctors","https://localhost/doctors?x=y"})
            runner.withPropertyValues(doctor).withPropertyValues("patient.reference.doctor.collection-uri="+uri)
                    .run(c -> assertThat(c).hasFailed());
    }
    @Test void credentialsCannotReuseIdentityOrEitherScopeAuthority() {
        for(String setting:new String[]{"patient.identity.client-id","patient.scope.administrative.client-id","patient.scope.clinical.client-id"}) {
            runner.withPropertyValues(doctor).withPropertyValues(setting+"=doctor-test-client").run(c -> assertThat(c).hasFailed());
        }
        runner.withPropertyValues(doctor).withPropertyValues("patient.reference.appointment.client-secret=doctor-test-secret")
                .run(c -> assertThat(c).hasFailed());
    }
    @Test void settingsNeverPrintCredentialsOrInternalUri() {
        try(var server=new TestReferenceServer(ReferenceHttpClient.Profile.DOCTOR)) {
            var s=server.settings(java.time.Duration.ofSeconds(3));
            assertThat(s.toString()).doesNotContain(server.secret,s.collectionUri().toString());
        }
    }
}

