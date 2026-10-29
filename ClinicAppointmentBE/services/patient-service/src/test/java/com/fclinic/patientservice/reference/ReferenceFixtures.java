package com.fclinic.patientservice.reference;

import com.fclinic.patientservice.scope.ScopeFixtures;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

public final class ReferenceFixtures {
    public static final JsonMapper JSON=JsonMapper.builder().build();
    public static final UUID PATIENT=ScopeFixtures.PATIENT;
    public static Map<String,Object> doctor(long id,boolean active) {
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("id",id); data.put("name","Synthetic Doctor"); data.put("specialty","Synthetic specialty");
        data.put("department","Synthetic department"); data.put("qualification",null); data.put("experienceYears",null);
        data.put("consultationFee",null); data.put("roomNumber",null); data.put("bio",null); data.put("avatarUrl",null);
        data.put("active",active); return success(data);
    }
    public static Map<String,Object> appointment(long id,UUID patient,long doctor,String status) {
        return success(new LinkedHashMap<>(Map.of("id",id,"patientId",patient.toString(),"doctorId",doctor,"status",status)));
    }
    public static Map<String,Object> success(Map<String,Object> data) { return new LinkedHashMap<>(Map.of("status",200,"message","Success","data",data)); }
    public static Map<String,Object> missing(String code) {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("status",404); result.put("message","Not found");
        result.put("data",null); result.put("code",code); result.put("fieldErrors",List.of()); return result;
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> data(Map<String,Object> body) { return (Map<String,Object>)body.get("data"); }
    public static byte[] bytes(Object value) { return JSON.writeValueAsBytes(value); }
}

