package com.fclinic.patientservice.api.controller;

import com.fclinic.common.web.ApiResponse;
import com.fclinic.patientservice.api.dto.CreatePatientRequest;
import com.fclinic.patientservice.api.dto.PatientResponse;
import com.fclinic.patientservice.application.command.RegisterPatientCommand;
import com.fclinic.patientservice.application.exception.PatientNotFoundException;
import com.fclinic.patientservice.application.port.in.GetPatientUseCase;
import com.fclinic.patientservice.application.port.in.ListPatientsUseCase;
import com.fclinic.patientservice.application.port.in.RegisterPatientUseCase;
import com.fclinic.patientservice.application.result.PatientView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final GetPatientUseCase getPatientUseCase;
    private final ListPatientsUseCase listPatientsUseCase;
    private final RegisterPatientUseCase registerPatientUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PatientResponse>>> getAllPatients() {
        List<PatientResponse> list = listPatientsUseCase.listAll()
                .stream().map(PatientResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PatientResponse>> getPatientById(@PathVariable Long id) {
        PatientView view = getPatientUseCase.getById(id)
                .orElseThrow(() -> new PatientNotFoundException("Không tìm thấy bệnh nhân ID: " + id));
        return ResponseEntity.ok(ApiResponse.success(PatientResponse.from(view)));
    }

    @GetMapping("/by-phone")
    public ResponseEntity<ApiResponse<PatientResponse>> getPatientByPhone(@RequestParam String phone) {
        PatientView view = getPatientUseCase.getByPhoneNumber(phone)
                .orElseThrow(() -> new PatientNotFoundException("Không tìm thấy bệnh nhân có SĐT: " + phone));
        return ResponseEntity.ok(ApiResponse.success(PatientResponse.from(view)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PatientResponse>> createOrUpdatePatient(@Valid @RequestBody CreatePatientRequest request) {
        RegisterPatientCommand command = new RegisterPatientCommand(
                request.getFullName(),
                request.getPhoneNumber(),
                request.getEmail(),
                request.getDateOfBirth(),
                request.getGender(),
                request.getAddress()
        );
        PatientView view = registerPatientUseCase.registerOrUpdate(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Lưu thông tin bệnh nhân thành công", PatientResponse.from(view)));
    }
}
