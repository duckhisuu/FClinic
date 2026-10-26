package com.fclinic.patientservice.infrastructure.adapter.scope;

import com.fclinic.patientservice.application.security.TrustedCaller;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;

/** Authenticated Patient assertion of verified context; authority resolves assignments from its OWN store. */
public record ScopeQuery(String contractVersion, UUID nonce, Instant requestedAt, Authority authority,
                         Operation operation, Caller caller, UUID patientId, Long appointmentId, Instant visitDate) {
    public static final String VERSION="patient-work-scope/1";
    public enum Authority { ADMINISTRATIVE, CLINICAL }
    public enum Operation {
        ADMIN_ASSIGNMENT_CREATE, ADMIN_ASSIGNMENT_READ, ADMIN_ASSIGNMENT_UPDATE, ADMIN_ASSIGNMENT_SEARCH,
        ADMIN_PROFILE_READ, ADMIN_PROFILE_UPDATE, ADMIN_SEARCH,
        DOCTOR_PROFILE_READ, DOCTOR_SEARCH, MEDICAL_READ, MEDICAL_CREATE, MEDICAL_UPDATE, VALID_VISIT,
        APPOINTMENT_PROFILE_READ
    }
    public record Caller(String subject, TrustedCaller.PrincipalType principalType, UUID patientId,
                         Long doctorId, String serviceName) {
        static Caller from(TrustedCaller c) { return new Caller(c.subject(),c.principalType(),c.patientId(),c.doctorId(),c.serviceName()); }
    }
    public static ScopeQuery create(Authority authority,Operation operation,TrustedCaller caller,UUID patient,
                                    Long appointment,Instant visit,Instant now) {
        return new ScopeQuery(VERSION,UUID.randomUUID(),now,authority,operation,Caller.from(caller),patient,appointment,visit);
    }
    public boolean search() { return operation==Operation.ADMIN_SEARCH || operation==Operation.DOCTOR_SEARCH; }
    public Map<String,Object> wire() {
        Map<String,Object> who=new LinkedHashMap<>();
        who.put("subject",caller.subject()); who.put("principalType",caller.principalType().name());
        who.put("patientId",caller.patientId()==null?null:caller.patientId().toString());
        who.put("doctorId",caller.doctorId()); who.put("serviceName",caller.serviceName());
        Map<String,Object> wire=new LinkedHashMap<>();
        wire.put("contractVersion",contractVersion); wire.put("nonce",nonce.toString()); wire.put("requestedAt",requestedAt.toString());
        wire.put("authority",authority.name()); wire.put("operation",operation.name()); wire.put("caller",who);
        wire.put("patientId",patientId==null?null:patientId.toString()); wire.put("appointmentId",appointmentId);
        wire.put("visitDate",visitDate==null?null:visitDate.toString()); return wire;
    }
}

