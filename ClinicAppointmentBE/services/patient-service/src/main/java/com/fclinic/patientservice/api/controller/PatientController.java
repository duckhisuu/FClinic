package com.fclinic.patientservice.api.controller;

import com.fclinic.common.web.ApiResponse;
import com.fclinic.patientservice.api.dto.*;
import com.fclinic.patientservice.application.security.PatientPolicy;
import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.application.usecase.PatientApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/patients")
public class PatientController {
    private final PatientApplicationService service;
    private final PatientPolicy policy;
    private final PatientInput input;
    public PatientController(PatientApplicationService service, PatientPolicy policy, PatientInput input) {
        this.service = service; this.policy = policy; this.input = input;
    }
    @PostMapping
    public ResponseEntity<?> create(@RequestAttribute("patientCaller") TrustedCaller c,
                                     @RequestBody Map<String,Object> body) {
        policy.createProfile(c);
        return ResponseEntity.status(201).body(ApiResponse.success(201,"Created",
                PatientResponse.from(service.create(c,input.profile(body,false)))));
    }
    @GetMapping
    public Object search(@RequestAttribute("patientCaller") TrustedCaller c, @RequestParam Map<String,String> params) {
        policy.search(c);
        return ApiResponse.success(service.search(c,input.query(params,false)).map(PatientResponse::from));
    }
    @GetMapping("/{id}")
    public Object get(@RequestAttribute("patientCaller") TrustedCaller c, @PathVariable String id) {
        return ApiResponse.success(PatientResponse.from(service.get(c,input.uuid(id,"id"))));
    }
    @PutMapping("/{id}")
    public Object replace(@RequestAttribute("patientCaller") TrustedCaller c, @PathVariable String id,
                          @RequestBody Map<String,Object> body) {
        UUID patientId = input.uuid(id,"id"); policy.replaceProfile(c,patientId);
        var data = input.profile(body,true);
        return ApiResponse.success(PatientResponse.from(service.replace(c,patientId,input.version(body),data)));
    }
    @PostMapping("/{patientId}/medical-records")
    public ResponseEntity<?> createRecord(@RequestAttribute("patientCaller") TrustedCaller c, @PathVariable String patientId,
                                           @RequestBody Map<String,Object> body) {
        UUID id = input.uuid(patientId,"patientId"); policy.prepareCreateMedical(c,id);
        var data = input.clinical(body,false);
        long doctorId = input.positiveId(body.get("doctorId"),"doctorId");
        Long appointmentId = body.get("appointmentId") == null ? null : input.positiveId(body.get("appointmentId"),"appointmentId");
        return ResponseEntity.status(201).body(ApiResponse.success(201,"Created",
                MedicalRecordResponse.from(service.createRecord(c,id,doctorId,appointmentId,data))));
    }
    @GetMapping("/{patientId}/medical-records")
    public Object records(@RequestAttribute("patientCaller") TrustedCaller c, @PathVariable String patientId,
                          @RequestParam Map<String,String> params) {
        UUID id = input.uuid(patientId,"patientId"); policy.readMedical(c,id);
        return ApiResponse.success(service.records(c,id,input.query(params,true)).map(MedicalRecordResponse::from));
    }
    @GetMapping("/{patientId}/medical-records/{recordId}")
    public Object record(@RequestAttribute("patientCaller") TrustedCaller c, @PathVariable String patientId,
                         @PathVariable String recordId) {
        return ApiResponse.success(MedicalRecordResponse.from(
                service.record(c,input.uuid(patientId,"patientId"),input.pathId(recordId,"recordId"))));
    }
    @PutMapping("/{patientId}/medical-records/{recordId}")
    public Object replaceRecord(@RequestAttribute("patientCaller") TrustedCaller c, @PathVariable String patientId,
                                 @PathVariable String recordId, @RequestBody Map<String,Object> body) {
        UUID id = input.uuid(patientId,"patientId"); policy.editMedical(c,id);
        var data = input.clinical(body,true);
        return ApiResponse.success(MedicalRecordResponse.from(service.replaceRecord(c,id,input.pathId(recordId,"recordId"),
                input.version(body),data,input.reason(body))));
    }
}
