package com.fclinic.authservice.api.controller;

import com.fclinic.authservice.api.mapper.AuthApiMapper;
import com.fclinic.authservice.application.model.*;
import com.fclinic.authservice.application.service.PatientOnboardingService;
import com.fclinic.common.web.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.util.*;

@RestController
@RequestMapping("/api/v1/auth/me/patient-onboarding")
public class PatientOnboardingController {
    private final PatientOnboardingService service;
    private final AuthApiMapper mapper;
    private final Clock clock;
    public PatientOnboardingController(PatientOnboardingService service, AuthApiMapper mapper, Clock clock) {
        this.service=service; this.mapper=mapper; this.clock=clock;
    }
    @GetMapping public Object status(@AuthenticationPrincipal AuthenticatedCaller caller) {
        Map<String,Object> state=new LinkedHashMap<>();
        state.put("profile",service.pending(caller).orElse(null));
        state.put("user",mapper.toResponse(service.owner(caller)));
        return ApiResponse.success(state);
    }
    @PostMapping public Object onboard(@AuthenticationPrincipal AuthenticatedCaller caller, @RequestBody Map<String,Object> body) {
        return ApiResponse.success(mapper.toResponse(service.onboard(caller,PatientOnboardingCommand.parse(body,clock))));
    }
}
