package com.fclinic.patientservice.api.controller;

import com.fclinic.patientservice.api.dto.PatientInput;
import com.fclinic.patientservice.application.usecase.ProvisionPatientService;
import com.fclinic.patientservice.domain.model.PatientError;
import com.fclinic.common.web.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.fclinic.patientservice.domain.model.PatientError.Code.*;

@RestController
@RequestMapping("/internal/v1/patient-onboarding")
public class PatientOnboardingController {
    private final PatientInput input;
    private final ProvisionPatientService service;
    private final String id, secret;
    private final JsonMapper json=JsonMapper.builder().build();
    public PatientOnboardingController(PatientInput input, ProvisionPatientService service,
        @Value("${patient.onboarding.client-id:auth-patient-onboarding}") String id,
        @Value("${patient.onboarding.client-secret:}") String secret) {
        this.input=input; this.service=service; this.id=id; this.secret=secret;
    }
    @PostMapping
    public Object ensure(@RequestHeader(value="Authorization",required=false) String authorization,
            @RequestBody Map<String,Object> body) {
        if (!authenticated(authorization)) throw new PatientError(UNAUTHENTICATED);
        if (body==null || !body.keySet().equals(Set.of("accountId","patientId","subject","requestHash","profile")))
            throw PatientError.invalid("body");
        UUID account=input.uuid(body.get("accountId"),"accountId"), patient=input.uuid(body.get("patientId"),"patientId");
        UUID subject=input.uuid(body.get("subject"),"subject");
        if (!(body.get("requestHash") instanceof String hash) || !hash.matches("[a-f0-9]{64}")
                || !(body.get("profile") instanceof Map<?,?> raw)) throw PatientError.invalid("body");
        Map<String,Object> profile=new TreeMap<>();
        raw.forEach((key,value)->profile.put(String.valueOf(key),value));
        try {
            String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(json.writeValueAsString(profile).getBytes(StandardCharsets.UTF_8)));
            if (!actual.equals(hash)) throw PatientError.invalid("requestHash");
        } catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        return ApiResponse.success(service.ensure(account,patient,subject.toString(),hash,input.profile(profile,false)));
    }
    private boolean authenticated(String header) {
        if (secret.isBlank() || id.isBlank() || header==null || header.length()>8192
                || !header.regionMatches(true,0,"Basic ",0,6)) return false;
        try {
            byte[] supplied=Base64.getDecoder().decode(header.substring(6));
            return MessageDigest.isEqual((id+":"+secret).getBytes(StandardCharsets.UTF_8),supplied);
        } catch(IllegalArgumentException ex) { return false; }
    }
}
