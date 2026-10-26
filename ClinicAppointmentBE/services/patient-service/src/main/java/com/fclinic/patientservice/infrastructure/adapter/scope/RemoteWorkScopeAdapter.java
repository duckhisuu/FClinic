package com.fclinic.patientservice.infrastructure.adapter.scope;

import com.fclinic.patientservice.application.port.out.WorkScopePort;
import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.domain.model.PatientError;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.*;
import static com.fclinic.patientservice.infrastructure.adapter.scope.ScopeQuery.Authority.*;
import static com.fclinic.patientservice.infrastructure.adapter.scope.ScopeQuery.Operation.*;

public final class RemoteWorkScopeAdapter implements WorkScopePort, AutoCloseable {
    private final ScopeSettings settings;
    private final ScopeAuthorityClient administrative, clinical;
    private final ScopeDecisionDecoder decoder;
    private final Clock clock;
    public RemoteWorkScopeAdapter(ScopeSettings settings,Clock clock) {
        settings.validate(); this.settings=settings; this.clock=clock; decoder=new ScopeDecisionDecoder(clock);
        administrative=new ScopeAuthorityClient(settings.administrative());
        try { clinical=new ScopeAuthorityClient(settings.clinical()); }
        catch(RuntimeException e) { administrative.close(); throw e; }
    }
    public boolean receptionist(TrustedCaller c) { return receptionist(c,AdministrativeAction.CREATE); }
    public boolean profile(TrustedCaller c,UUID id) {
        return profile(c,id,c.userRole("ROLE_STAFF")?ProfileAction.ADMIN_READ:ProfileAction.DOCTOR_READ);
    }
    public boolean clinical(TrustedCaller c,UUID id) { return clinical(c,id,ClinicalAction.READ); }
    public SearchScope search(TrustedCaller c) {
        return search(c,c.userRole("ROLE_STAFF")?SearchAction.ADMINISTRATIVE:SearchAction.CLINICAL);
    }
    public boolean receptionist(TrustedCaller c,AdministrativeAction action) {
        return c.userRole("ROLE_STAFF") && query(c,ADMINISTRATIVE,
                ScopeQuery.Operation.valueOf("ADMIN_ASSIGNMENT_"+action.name()),null,null,null).allowed();
    }
    public boolean profile(TrustedCaller c,UUID id,ProfileAction action) {
        boolean doctor=action==ProfileAction.DOCTOR_READ;
        if(doctor?!doctor(c):!c.userRole("ROLE_STAFF")) return false;
        return query(c,doctor?CLINICAL:ADMINISTRATIVE,ScopeQuery.Operation.valueOf(action==ProfileAction.DOCTOR_READ
                ?"DOCTOR_PROFILE_READ":action==ProfileAction.ADMIN_READ?"ADMIN_PROFILE_READ":"ADMIN_PROFILE_UPDATE"),id,null,null).allowed();
    }
    public boolean clinical(TrustedCaller c,UUID id,ClinicalAction action) {
        return doctor(c) && query(c,CLINICAL,ScopeQuery.Operation.valueOf("MEDICAL_"+action.name()),id,null,null).allowed();
    }
    public boolean validVisit(TrustedCaller c,UUID id,Long appointmentId,Instant visitDate) {
        return doctor(c) && query(c,CLINICAL,VALID_VISIT,id,appointmentId,visitDate).allowed();
    }
    public boolean appointmentProfileLookup(TrustedCaller c,UUID id) {
        return c.principalType()==TrustedCaller.PrincipalType.SERVICE && "appointment-service".equals(c.serviceName())
                && c.authorities().contains("SVC_PATIENT_READ")
                && query(c,ADMINISTRATIVE,APPOINTMENT_PROFILE_READ,id,null,null).allowed();
    }
    public SearchScope search(TrustedCaller c,SearchAction action) {
        boolean admin=action==SearchAction.ADMINISTRATIVE;
        if(admin?!c.userRole("ROLE_STAFF"):!doctor(c)) return null;
        var decision=query(c,admin?ADMINISTRATIVE:CLINICAL,admin?ADMIN_SEARCH:DOCTOR_SEARCH,null,null,null);
        return decision.allowed()?new SearchScope(decision.patientIds()):null;
    }
    private ScopeDecisionDecoder.Decision query(TrustedCaller c,ScopeQuery.Authority authority,ScopeQuery.Operation operation,
                                               UUID patient,Long appointment,Instant visit) {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new PatientError(PatientError.Code.DEPENDENCY_UNAVAILABLE);
        boolean collection=operation.name().startsWith("ADMIN_ASSIGNMENT_") || operation==ADMIN_SEARCH || operation==DOCTOR_SEARCH;
        if(!c.active() || c.subject()==null || c.subject().isBlank() || (!collection && patient==null)
                || (operation==VALID_VISIT && (visit==null || (appointment!=null && appointment<=0))))
            throw new PatientError(PatientError.Code.DEPENDENCY_UNAVAILABLE);
        var request=ScopeQuery.create(authority,operation,c,patient,appointment,visit,clock.instant());
        var config=authority==ADMINISTRATIVE?settings.administrative():settings.clinical();
        byte[] response=(authority==ADMINISTRATIVE?administrative:clinical).evaluate(request);
        return decoder.decode(response,request,config.maxSearchPatients());
    }
    private static boolean doctor(TrustedCaller c) {
        return c.userRole("ROLE_DOCTOR") && c.doctorId()!=null && c.doctorId()>0;
    }
    public void close() { try { administrative.close(); } finally { clinical.close(); } }
}

