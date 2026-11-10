package com.fclinic.patientservice.api.controller;
import com.fclinic.common.web.ApiResponse;
import com.fclinic.patientservice.api.dto.*;
import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.application.usecase.PatientApplicationService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v3/patients/reception-lookup")
public class ReceptionLookupController {
    private final PatientApplicationService service;private final PatientInput input;
    public ReceptionLookupController(PatientApplicationService service,PatientInput input){this.service=service;this.input=input;}
    @GetMapping public Object lookup(@RequestAttribute("patientCaller") TrustedCaller caller,@RequestParam Map<String,String> params){
        return ApiResponse.success(service.receptionLookup(caller,input.query(params,false)).map(PatientResponse::from));
    }
}
