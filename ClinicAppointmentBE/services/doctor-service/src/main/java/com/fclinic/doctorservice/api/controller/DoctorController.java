package com.fclinic.doctorservice.api.controller;

import com.fclinic.common.web.ApiResponse;
import com.fclinic.doctorservice.api.dto.CreateDoctorRequest;
import com.fclinic.doctorservice.api.dto.CreateTimeSlotRequest;
import com.fclinic.doctorservice.api.dto.DoctorResponse;
import com.fclinic.doctorservice.api.dto.TimeSlotResponse;
import com.fclinic.doctorservice.application.command.CreateDoctorCommand;
import com.fclinic.doctorservice.application.command.CreateTimeSlotCommand;
import com.fclinic.doctorservice.application.exception.DoctorNotFoundException;
import com.fclinic.doctorservice.application.port.in.*;
import com.fclinic.doctorservice.application.result.DoctorView;
import com.fclinic.doctorservice.application.result.TimeSlotView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final GetDoctorUseCase getDoctorUseCase;
    private final ListDoctorsUseCase listDoctorsUseCase;
    private final CreateDoctorUseCase createDoctorUseCase;
    private final ListTimeSlotsUseCase listTimeSlotsUseCase;
    private final CreateTimeSlotUseCase createTimeSlotUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DoctorResponse>>> getDoctors(
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String department
    ) {
        List<DoctorResponse> doctors = listDoctorsUseCase.listDoctors(specialty, department)
                .stream().map(DoctorResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(doctors));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorResponse>> getDoctorById(@PathVariable Long id) {
        DoctorView view = getDoctorUseCase.getById(id)
                .orElseThrow(() -> new DoctorNotFoundException("Không tìm thấy bác sĩ ID: " + id));
        return ResponseEntity.ok(ApiResponse.success(DoctorResponse.from(view)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DoctorResponse>> createDoctor(@Valid @RequestBody CreateDoctorRequest request) {
        CreateDoctorCommand command = new CreateDoctorCommand(
                request.getName(),
                request.getSpecialty(),
                request.getDepartment(),
                request.getQualification(),
                request.getExperienceYears(),
                request.getConsultationFee(),
                request.getRoomNumber(),
                request.getBio(),
                request.getAvatarUrl()
        );
        DoctorView view = createDoctorUseCase.createDoctor(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Thêm bác sĩ thành công", DoctorResponse.from(view)));
    }

    @GetMapping("/{id}/slots")
    public ResponseEntity<ApiResponse<List<TimeSlotResponse>>> getDoctorSlots(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        List<TimeSlotResponse> slots = listTimeSlotsUseCase.listSlots(id, date)
                .stream().map(TimeSlotResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(slots));
    }

    @PostMapping("/{id}/slots")
    public ResponseEntity<ApiResponse<TimeSlotResponse>> createSlot(
            @PathVariable Long id,
            @Valid @RequestBody CreateTimeSlotRequest request
    ) {
        CreateTimeSlotCommand command = new CreateTimeSlotCommand(
                id,
                request.getSlotDate(),
                request.getStartTime(),
                request.getEndTime()
        );
        TimeSlotView view = createTimeSlotUseCase.createTimeSlot(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Thêm khung giờ thành công", TimeSlotResponse.from(view)));
    }
}
