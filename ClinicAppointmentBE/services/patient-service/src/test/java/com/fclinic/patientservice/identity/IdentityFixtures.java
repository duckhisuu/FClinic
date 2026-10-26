package com.fclinic.patientservice.identity;

import com.fclinic.patientservice.infrastructure.adapter.identity.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.time.*;
import java.util.*;

/** Synthetic local contract vectors only; never a production identity provider. */
public final class IdentityFixtures {
    public static final JsonMapper JSON = JsonMapper.builder().build();
    public static final Clock CLOCK = registryClock();
    public static final UUID PATIENT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    public static final String SERVICE_SUBJECT = "opaque-machine-fixture";
    public static final String CLIENT_ID = "patient-test-client";
    public static Map<String,Object> user() {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("active",true); data.put("contractVersion",IntrospectionDecoder.VERSION);
        data.put("iss","https://identity.example.test"); data.put("aud",List.of("patient-service"));
        data.put("exp",CLOCK.instant().plusSeconds(600).getEpochSecond());
        data.put("sub","opaque-human-fixture"); data.put("principalType","USER"); data.put("userId",401L);
        data.put("patientId",PATIENT.toString()); data.put("doctorId",null);
        data.put("authorities",List.of("ROLE_PATIENT"));
        return data;
    }
    public static Map<String,Object> service() {
        var data = user(); data.put("principalType","SERVICE"); data.put("sub",SERVICE_SUBJECT);
        data.put("userId",null); data.put("patientId",null); data.put("doctorId",null);
        data.put("authorities",List.of("SVC_PATIENT_READ")); return data;
    }
    public static IdentitySettings settings() {
        return new IdentitySettings(true,URI.create("https://localhost/introspection"),"https://identity.example.test",
                "patient-service",CLIENT_ID,UUID.randomUUID().toString(),Duration.ofSeconds(1),Duration.ofSeconds(3),
                65536,32,null,null,Map.of(SERVICE_SUBJECT,
                new IdentitySettings.ServiceBinding("appointment-service",Set.of("SVC_PATIENT_READ"))));
    }
    public static byte[] bytes(Map<String,Object> body) { return JSON.writeValueAsBytes(body); }
    private static Clock registryClock() {
        try(var in=IdentityFixtures.class.getResourceAsStream("/fixtures/registry.json")) {
            return Clock.fixed(Instant.parse(JSON.readTree(in).path("clock").asText()),ZoneOffset.UTC);
        } catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
}

