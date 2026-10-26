package com.fclinic.patientservice.application.security;

import com.fclinic.patientservice.application.port.out.WorkScopePort;
import com.fclinic.patientservice.domain.model.PatientError;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import static com.fclinic.patientservice.domain.model.PatientError.Code.*;
import static com.fclinic.patientservice.application.port.out.WorkScopePort.*;

@Component
public class PatientPolicy {
    private final WorkScopePort scopes;
    public PatientPolicy(WorkScopePort scopes) { this.scopes = scopes; }
    public void authenticated(TrustedCaller c) {
        if (c == null || !c.active() || c.principalType() == null || c.subject() == null
                || c.subject().isBlank() || c.subject().length() > 255) throw new PatientError(UNAUTHENTICATED);
    }
    private void administrative(TrustedCaller c, AdministrativeAction action) {
        authenticated(c);
        allow(c.userRole("ROLE_STAFF") && scope(() -> scopes.receptionist(c,action)));
    }
    public void createProfile(TrustedCaller c) { administrative(c,AdministrativeAction.CREATE); }
    public void replaceProfile(TrustedCaller c, UUID id) {
        administrative(c,AdministrativeAction.UPDATE);
        allow(scope(() -> scopes.profile(c,id,ProfileAction.ADMIN_UPDATE)));
    }
    public void readProfile(TrustedCaller c, UUID id) {
        authenticated(c);
        allow((c.userRole("ROLE_PATIENT") && id.equals(c.patientId()))
            || (c.userRole("ROLE_STAFF") && scope(() -> scopes.receptionist(c,AdministrativeAction.READ))
                && scope(() -> scopes.profile(c,id,ProfileAction.ADMIN_READ)))
            || (c.userRole("ROLE_DOCTOR") && scope(() -> scopes.profile(c,id,ProfileAction.DOCTOR_READ)))
            || (c.principalType() == TrustedCaller.PrincipalType.SERVICE
                && "appointment-service".equals(c.serviceName()) && scope(() -> scopes.appointmentProfileLookup(c,id))));
    }
    public Set<UUID> search(TrustedCaller c) {
        authenticated(c);
        boolean administrative = c.userRole("ROLE_STAFF") && scope(() -> scopes.receptionist(c,AdministrativeAction.SEARCH));
        allow(administrative || c.userRole("ROLE_DOCTOR"));
        WorkScopePort.SearchScope result = scope(() -> scopes.search(c,
                administrative ? SearchAction.ADMINISTRATIVE : SearchAction.CLINICAL));
        allow(result != null);
        return result.patientIds();
    }
    public void readMedical(TrustedCaller c, UUID id) {
        authenticated(c);
        allow((c.userRole("ROLE_PATIENT") && id.equals(c.patientId()))
                || (c.userRole("ROLE_DOCTOR") && scope(() -> scopes.clinical(c,id,ClinicalAction.READ))));
    }
    public void prepareCreateMedical(TrustedCaller c, UUID id) {
        authenticated(c);
        allow(c.userRole("ROLE_DOCTOR") && c.doctorId() != null
                && scope(() -> scopes.clinical(c,id,ClinicalAction.CREATE)));
    }
    public void createMedical(TrustedCaller c, UUID id, long doctorId, Long appointmentId, Instant visit) {
        authenticated(c);
        allow(c.userRole("ROLE_DOCTOR") && c.doctorId() != null && c.doctorId() == doctorId
                && scope(() -> scopes.clinical(c,id,ClinicalAction.CREATE))
                && scope(() -> scopes.validVisit(c,id,appointmentId,visit)));
    }
    public void editMedical(TrustedCaller c, UUID id) {
        authenticated(c);
        allow(c.userRole("ROLE_DOCTOR") && c.doctorId() != null
                && scope(() -> scopes.clinical(c,id,ClinicalAction.UPDATE)));
    }
    /** F-06: translate technical scope failure here, never classify unrelated DB/domain failures as503. */
    private static <T> T scope(Supplier<T> call) {
        try { return call.get(); }
        catch (PatientError error) {
            if (error.code() == FORBIDDEN || error.code() == DEPENDENCY_UNAVAILABLE) throw error;
            throw new PatientError(DEPENDENCY_UNAVAILABLE);
        } catch (RuntimeException error) { throw new PatientError(DEPENDENCY_UNAVAILABLE); }
    }
    public static void allow(boolean condition) { if (!condition) throw new PatientError(FORBIDDEN); }
}

