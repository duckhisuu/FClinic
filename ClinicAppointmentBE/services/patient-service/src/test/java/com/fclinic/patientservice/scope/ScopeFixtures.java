package com.fclinic.patientservice.scope;

import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.infrastructure.adapter.scope.*;
import com.fclinic.patientservice.identity.IdentityFixtures;
import java.time.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

public final class ScopeFixtures {
    public static final Clock CLOCK=IdentityFixtures.CLOCK;
    public static final UUID PATIENT=IdentityFixtures.PATIENT;
    public static final JsonMapper JSON=JsonMapper.builder().findAndAddModules().build();
    public static final TrustedCaller STAFF=new TrustedCaller("opaque-staff-fixture",TrustedCaller.PrincipalType.USER,
            Set.of("ROLE_STAFF"),null,null,null,true);
    public static final TrustedCaller DOCTOR=new TrustedCaller("opaque-doctor-fixture",TrustedCaller.PrincipalType.USER,
            Set.of("ROLE_DOCTOR"),null,12L,null,true);
    public static ScopeQuery query(ScopeQuery.Operation operation) {
        boolean admin=operation.name().startsWith("ADMIN") || operation==ScopeQuery.Operation.APPOINTMENT_PROFILE_READ;
        boolean collection=operation.name().startsWith("ADMIN_ASSIGNMENT") || operation.name().endsWith("SEARCH");
        return ScopeQuery.create(admin?ScopeQuery.Authority.ADMINISTRATIVE:ScopeQuery.Authority.CLINICAL,
                operation,admin?STAFF:DOCTOR,collection?null:PATIENT,
                operation==ScopeQuery.Operation.VALID_VISIT?102L:null,
                operation==ScopeQuery.Operation.VALID_VISIT?CLOCK.instant().minusSeconds(60):null,CLOCK.instant());
    }
    public static Map<String,Object> grant(Map<String,Object> request,Collection<UUID> patients) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("contractVersion",ScopeQuery.VERSION); result.put("request",request);
        result.put("evaluatedAt",CLOCK.instant().toString()); result.put("allowed",true);
        boolean search=Set.of("ADMIN_SEARCH","DOCTOR_SEARCH").contains(request.get("operation"));
        result.put("patientIds",search?patients.stream().map(UUID::toString).toList():null);
        Map<String,Object> evidence=new LinkedHashMap<>();
        evidence.put("assignmentId","opaque-assignment-fixture"); evidence.put("active",true);
        evidence.put("validFrom",CLOCK.instant().minusSeconds(3600).toString());
        evidence.put("validUntil",CLOCK.instant().plusSeconds(300).toString());
        evidence.put("visitId","VALID_VISIT".equals(request.get("operation"))?"opaque-encounter-fixture":null);
        result.put("evidence",evidence); return result;
    }
    public static Map<String,Object> deny(Map<String,Object> request) {
        var result=grant(request,Set.of()); result.put("allowed",false); result.put("patientIds",null); result.put("evidence",null);
        return result;
    }
    public static byte[] bytes(Object value) { return JSON.writeValueAsBytes(value); }
    @SuppressWarnings("unchecked") public static Map<String,Object> nested(Map<String,Object> root,String key) {
        return (Map<String,Object>)root.get(key);
    }
}

