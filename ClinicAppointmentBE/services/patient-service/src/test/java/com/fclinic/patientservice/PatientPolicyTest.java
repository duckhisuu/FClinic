package com.fclinic.patientservice;
import com.fclinic.patientservice.application.port.out.WorkScopePort;
import com.fclinic.patientservice.application.security.*;
import com.fclinic.patientservice.domain.model.PatientError;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatientPolicyTest {
    private final WorkScopePort scopes=mock(WorkScopePort.class,CALLS_REAL_METHODS);
    private final PatientPolicy policy=new PatientPolicy(scopes);
    private final UUID a=UUID.randomUUID(), b=UUID.randomUUID();
    private TrustedCaller user(String role, UUID patient, Long doctor) {
        return new TrustedCaller("user:demo",TrustedCaller.PrincipalType.USER,Set.of(role),patient,doctor,null,true);
    }
    @Test void tc18_patientCanReadSelfOnlyAndCannotMutateOrSearch() {
        var c=user("ROLE_PATIENT",a,null);
        policy.readProfile(c,a); policy.readMedical(c,a);
        denied(() -> policy.readProfile(c,b)); denied(() -> policy.readMedical(c,b));
        denied(() -> policy.createProfile(c)); denied(() -> policy.search(c));
        denied(() -> policy.replaceProfile(c,a)); denied(() -> policy.editMedical(c,a));
    }
    @Test void tc35_doctorRoleAloneGrantsNoScope() {
        var c=user("ROLE_DOCTOR",null,12L);
        denied(() -> policy.readMedical(c,a)); denied(() -> policy.readProfile(c,a));
        denied(() -> policy.createMedical(c,a,12,null,Instant.EPOCH)); denied(() -> policy.search(c));
        when(scopes.clinical(c,a)).thenReturn(true);
        policy.readMedical(c,a);
        denied(() -> policy.createMedical(c,a,12,null,Instant.EPOCH));
        when(scopes.validVisit(c,a,null,Instant.EPOCH)).thenReturn(true);
        policy.createMedical(c,a,12,null,Instant.EPOCH);
        denied(() -> policy.createMedical(c,a,13,null,Instant.EPOCH));
    }
    @Test void tc18_tc36_staffAndAdminHaveNoClinicalOverride() {
        var staff=user("ROLE_STAFF",null,null);
        denied(() -> policy.createProfile(staff));
        when(scopes.receptionist(staff)).thenReturn(true);
        policy.createProfile(staff);
        denied(() -> policy.readMedical(staff,a)); denied(() -> policy.editMedical(staff,a));
        var admin=user("ROLE_ADMIN",null,null);
        denied(() -> policy.readMedical(admin,a)); denied(() -> policy.editMedical(admin,a));
    }
    @Test void tc18_serviceNeedsExplicitAppointmentLookupGrant() {
        var service=new TrustedCaller("svc:demo",TrustedCaller.PrincipalType.SERVICE,Set.of("ROLE_ADMIN"),
                null,null,"appointment-service",true);
        denied(() -> policy.readProfile(service,a)); denied(() -> policy.search(service));
        when(scopes.appointmentProfileLookup(service,a)).thenReturn(true);
        policy.readProfile(service,a); denied(() -> policy.readMedical(service,a));
    }
    @Test void searchReturnsOnlyTrustedScope() {
        var c=user("ROLE_DOCTOR",null,12L);
        when(scopes.search(c)).thenReturn(new WorkScopePort.SearchScope(Set.of(a)));
        assertThat(policy.search(c)).containsExactly(a);
    }
    private void denied(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(PatientError.class,
                e -> assertThat(e.code()).isEqualTo(PatientError.Code.FORBIDDEN));
    }
}
