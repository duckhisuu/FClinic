package com.fclinic.appointmentservice.api.controller;

import com.fclinic.appointmentservice.api.dto.AppointmentResponse;
import com.fclinic.appointmentservice.api.dto.BookAppointmentRequest;
import com.fclinic.appointmentservice.application.command.BookAppointmentCommand;
import com.fclinic.appointmentservice.application.command.CancelAppointmentCommand;
import com.fclinic.appointmentservice.application.exception.AppointmentNotFoundException;
import com.fclinic.appointmentservice.application.port.in.BookAppointmentUseCase;
import com.fclinic.appointmentservice.application.port.in.CancelAppointmentUseCase;
import com.fclinic.appointmentservice.application.port.in.GetAppointmentUseCase;
import com.fclinic.appointmentservice.application.port.in.ListAppointmentsUseCase;
import com.fclinic.appointmentservice.application.result.AppointmentView;
import com.fclinic.common.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Slf4j
public class AppointmentController {

    private final BookAppointmentUseCase bookAppointmentUseCase;
    private final CancelAppointmentUseCase cancelAppointmentUseCase;
    private final GetAppointmentUseCase getAppointmentUseCase;
    private final ListAppointmentsUseCase listAppointmentsUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAllAppointments() {
        List<AppointmentResponse> list = listAppointmentsUseCase.getAll()
                .stream().map(AppointmentResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointmentById(@PathVariable Long id) {
        AppointmentView view = getAppointmentUseCase.getById(id)
                .orElseThrow(() -> new AppointmentNotFoundException("Không tìm thấy cuộc hẹn ID: " + id));
        return ResponseEntity.ok(ApiResponse.success(AppointmentResponse.from(view)));
    }

    @GetMapping("/code/{bookingCode}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointmentByCode(@PathVariable String bookingCode) {
        AppointmentView view = getAppointmentUseCase.getByBookingCode(bookingCode)
                .orElseThrow(() -> new AppointmentNotFoundException("Không tìm thấy cuộc hẹn có mã: " + bookingCode));
        return ResponseEntity.ok(ApiResponse.success(AppointmentResponse.from(view)));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAppointmentsByPatient(@PathVariable Long patientId) {
        List<AppointmentResponse> list = listAppointmentsUseCase.getByPatientId(patientId)
                .stream().map(AppointmentResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/by-phone")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAppointmentsByPhone(@RequestParam String phone) {
        List<AppointmentResponse> list = listAppointmentsUseCase.getByPatientPhone(phone)
                .stream().map(AppointmentResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponse>> bookAppointment(@Valid @RequestBody BookAppointmentRequest request) {
        BookAppointmentCommand command = new BookAppointmentCommand(
                request.getPatientId(),
                request.getPatientName(),
                request.getPatientPhone(),
                request.getDoctorId(),
                request.getDoctorName(),
                request.getDoctorSpecialty(),
                request.getSlotId(),
                request.getAppointmentDate(),
                request.getStartTime(),
                request.getEndTime(),
                request.getReasonForVisit(),
                request.getConsultationFee()
        );

        AppointmentView view = bookAppointmentUseCase.bookAppointment(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Đặt lịch khám thành công", AppointmentResponse.from(view)));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancelAppointment(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Bệnh nhân yêu cầu hủy") String reason
    ) {
        CancelAppointmentCommand command = new CancelAppointmentCommand(id, reason);
        AppointmentView view = cancelAppointmentUseCase.cancelAppointment(command);
        return ResponseEntity.ok(ApiResponse.success("Hủy lịch khám thành công", AppointmentResponse.from(view)));
    }
}
