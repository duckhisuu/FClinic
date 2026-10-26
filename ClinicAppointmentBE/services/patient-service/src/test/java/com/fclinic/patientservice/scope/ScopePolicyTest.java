package com.fclinic.patientservice.scope;

import com.fclinic.patientservice.application.port.out.WorkScopePort;
import com.fclinic.patientservice.application.security.*;
import com.fclinic.patientservice.domain.model.PatientError;
import org.junit.jupiter.api.Test;
import static com.fclinic.patientservice.scope.ScopeFixtures.*;
import static com.fclinic.patientservice.application.port.out.WorkScopePort.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScopePolicyTest {
    final WorkScopePort scopes=mock(WorkScopePort.class);
    final PatientPolicy policy=new PatientPolicy(scopes);
    @Test void clinicalCreateReadAndUpdateNeedDifferentDecisions() {
        when(scopes.clinical(DOCTOR,PATIENT,ClinicalAction.CREATE)).thenReturn(true);
        when(scopes.validVisit(DOCTOR,PATIENT,null,CLOCK.instant())).thenReturn(true);
        policy.prepareCreateMedical(DOCTOR,PATIENT);
        policy.createMedical(DOCTOR,PATIENT,12,null,CLOCK.instant());
        denied(() -> policy.readMedical(DOCTOR,PATIENT));
        denied(() -> policy.editMedical(DOCTOR,PATIENT));
        denied(() -> policy.createMedical(DOCTOR,PATIENT,13,null,CLOCK.instant()));
    }
    @Test void administrativeActionDoesNotGrantOtherOperations() {
        when(scopes.receptionist(STAFF,AdministrativeAction.CREATE)).thenReturn(true);
        policy.createProfile(STAFF);
        denied(() -> policy.replaceProfile(STAFF,PATIENT)); denied(() -> policy.readProfile(STAFF,PATIENT));
        denied(() -> policy.search(STAFF)); denied(() -> policy.readMedical(STAFF,PATIENT));
    }
    @Test void nullAndEmptySearchKeep403VersusAuthorizedEmpty() {
        when(scopes.receptionist(STAFF,AdministrativeAction.SEARCH)).thenReturn(true);
        denied(() -> policy.search(STAFF));
        when(scopes.search(STAFF,SearchAction.ADMINISTRATIVE)).thenReturn(new SearchScope(java.util.Set.of()));
        assertThat(policy.search(STAFF)).isEmpty();
    }
    @Test void f06UnexpectedScopeExceptionBecomes503Not500AndDenialStays403() {
        when(scopes.clinical(DOCTOR,PATIENT,ClinicalAction.READ)).thenThrow(new IllegalStateException("private dependency detail"));
        assertThatThrownBy(() -> policy.readMedical(DOCTOR,PATIENT)).isInstanceOfSatisfying(PatientError.class,e -> {
            assertThat(e.code()).isEqualTo(PatientError.Code.DEPENDENCY_UNAVAILABLE);
            assertThat(e.getMessage()).isEqualTo("DEPENDENCY_UNAVAILABLE"); assertThat(e.getCause()).isNull();
        });
        reset(scopes); denied(() -> policy.readMedical(DOCTOR,PATIENT));
    }
    private void denied(Runnable r) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(PatientError.class,e -> assertThat(e.code()).isEqualTo(PatientError.Code.FORBIDDEN));
    }
}

