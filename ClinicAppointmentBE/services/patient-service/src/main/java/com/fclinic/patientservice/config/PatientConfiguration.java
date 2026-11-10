package com.fclinic.patientservice.config;

import com.fclinic.patientservice.application.port.out.*;
import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.domain.model.PatientError;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Configuration
public class PatientConfiguration {
    @Bean @ConditionalOnMissingBean
    @ConditionalOnProperty(name="patient.reception.enabled",havingValue="false",matchIfMissing=true)
    ReceptionAssignmentPort receptionAssignments() {
        return new ReceptionAssignmentPort() {
            public boolean enabled(){return false;}
            public void assign(TrustedCaller c,java.util.Set<UUID> ids,String action){throw new PatientError(PatientError.Code.DEPENDENCY_UNAVAILABLE);}
        };
    }
    @Bean @ConditionalOnMissingBean
    Clock patientClock() { return Clock.systemUTC(); }
    @Bean @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "patient.identity.enabled", havingValue = "false", matchIfMissing = true)
    IdentityVerifierPort identityVerifier() {
        return token -> { throw new PatientError(PatientError.Code.DEPENDENCY_UNAVAILABLE); };
    }
    @Bean @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "patient.reference.doctor.enabled", havingValue = "false", matchIfMissing = true)
    DoctorClientPort doctorClient() {
        return id -> { throw new PatientError(PatientError.Code.DEPENDENCY_UNAVAILABLE); };
    }
    @Bean @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "patient.reference.appointment.enabled", havingValue = "false", matchIfMissing = true)
    AppointmentClientPort appointmentClient() {
        return id -> { throw new PatientError(PatientError.Code.DEPENDENCY_UNAVAILABLE); };
    }
    @Bean @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "patient.scope.enabled", havingValue = "false", matchIfMissing = true)
    WorkScopePort workScope() {
        return new WorkScopePort() {
            public boolean receptionist(TrustedCaller c) { return false; }
            public boolean profile(TrustedCaller c, UUID p) { return false; }
            public boolean clinical(TrustedCaller c, UUID p) { return false; }
            public boolean validVisit(TrustedCaller c, UUID p, UUID a, Instant v) { return false; }
            public boolean appointmentProfileLookup(TrustedCaller c, UUID p) { return false; }
            public SearchScope search(TrustedCaller c) { return null; }
        };
    }
}
