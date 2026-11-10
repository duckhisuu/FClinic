package com.fclinic.patientservice.api.controller;

import com.fclinic.common.web.ApiResponse;
import com.fclinic.patientservice.api.dto.PatientResponse;
import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.application.usecase.PatientApplicationService;
import org.springframework.web.bind.annotation.*;

/** Read only the initial profile owned by the independently verified caller. */
@RestController
@RequestMapping("/api/v3/patients/self-profile")
public class PatientOnboardingController {
    private final PatientApplicationService service;
    public PatientOnboardingController(PatientApplicationService service) { this.service = service; }
    @GetMapping
    public Object selfProfile(@RequestAttribute("patientCaller") TrustedCaller caller) {
        return ApiResponse.success(PatientResponse.from(service.selfProfile(caller)));
    }
}
