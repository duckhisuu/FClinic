package com.fclinic.doctorservice.api.controller;

import com.fclinic.common.web.ApiResponse;
import com.fclinic.doctorservice.api.dto.CreateDoctorRequest;
import com.fclinic.doctorservice.api.dto.CreateTimeSlotRequest;
import com.fclinic.doctorservice.api.dto.DoctorResponse;
import com.fclinic.doctorservice.api.dto.TimeSlotResponse;
import com.fclinic.doctorservice.api.dto.DoctorScheduleResponse;
import com.fclinic.doctorservice.api.mapper.DoctorApiMapper;
import com.fclinic.doctorservice.application.exception.DoctorNotFoundException;
import com.fclinic.doctorservice.application.model.SchedulePeriod;
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
    private final GetDoctorScheduleUseCase getDoctorScheduleUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DoctorResponse>>> getDoctors(
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String department
    ) {
        List<DoctorResponse> doctors = listDoctorsUseCase.listDoctors(specialty, department)
                .stream().map(DoctorApiMapper::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(doctors));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorResponse>> getDoctorById(@PathVariable Long id) {
        DoctorView view = getDoctorUseCase.getById(id)
                .orElseThrow(() -> new DoctorNotFoundException("Không tìm thấy bác sĩ ID: " + id));
        return ResponseEntity.ok(ApiResponse.success(DoctorApiMapper.toResponse(view)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DoctorResponse>> createDoctor(@Valid @RequestBody CreateDoctorRequest request) {
        DoctorView view = createDoctorUseCase.createDoctor(DoctorApiMapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Thêm bác sĩ thành công", DoctorApiMapper.toResponse(view)));
    }

    @GetMapping("/{id}/slots")
    public ResponseEntity<ApiResponse<List<TimeSlotResponse>>> getDoctorSlots(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        List<TimeSlotResponse> slots = listTimeSlotsUseCase.listSlots(id, date)
                .stream().map(DoctorApiMapper::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(slots));
    }

    @GetMapping("/{id}/schedule")
    public ResponseEntity<ApiResponse<DoctorScheduleResponse>> getDoctorSchedule(
            @PathVariable Long id,
            @RequestParam(defaultValue = "WEEK") SchedulePeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchorDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                DoctorApiMapper.toResponse(getDoctorScheduleUseCase.getSchedule(id, period, anchorDate))));
    }

    @PostMapping("/{id}/slots")
    public ResponseEntity<ApiResponse<TimeSlotResponse>> createSlot(
            @PathVariable Long id,
            @Valid @RequestBody CreateTimeSlotRequest request
    ) {
        TimeSlotView view = createTimeSlotUseCase.createTimeSlot(DoctorApiMapper.toCommand(id, request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Thêm khung giờ thành công", DoctorApiMapper.toResponse(view)));
    }
}
