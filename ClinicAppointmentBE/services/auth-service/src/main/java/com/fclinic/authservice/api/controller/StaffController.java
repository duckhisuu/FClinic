package com.fclinic.authservice.api.controller;

import com.fclinic.authservice.api.dto.request.StaffCreatePatientRequest;
import com.fclinic.authservice.api.dto.response.UserResponse;
import com.fclinic.authservice.api.mapper.AuthApiMapper;
import com.fclinic.authservice.application.port.in.StaffUseCase;
import com.fclinic.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/staff")
@PreAuthorize("hasAnyAuthority('ROLE_STAFF', 'ROLE_ADMIN')")
public class StaffController {

    private final StaffUseCase staffUseCase;
    private final AuthApiMapper mapper;

    public StaffController(StaffUseCase staffUseCase, AuthApiMapper mapper) {
        this.staffUseCase = staffUseCase;
        this.mapper = mapper;
    }

    @GetMapping("/patients/lookup")
    public ResponseEntity<ApiResponse<UserResponse>> lookupPatient(
            @RequestParam("query") String query
    ) {
        UserResponse response = mapper.toResponse(staffUseCase.lookupPatient(query));
        return ResponseEntity.ok(ApiResponse.success("Tra cứu tài khoản bệnh nhân thành công", response));
    }

    @PostMapping("/patients")
    public ResponseEntity<ApiResponse<UserResponse>> createPatientOnSite(
            @Valid @RequestBody StaffCreatePatientRequest request
    ) {
        UserResponse response = mapper.toResponse(staffUseCase.createPatientOnSite(mapper.toCommand(request)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Tạo tài khoản bệnh nhân tại quầy thành công", response));
    }
}

