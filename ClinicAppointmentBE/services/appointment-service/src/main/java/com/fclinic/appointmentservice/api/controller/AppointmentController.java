package com.fclinic.appointmentservice.api.controller;

import com.fclinic.appointmentservice.api.dto.*;
import com.fclinic.appointmentservice.api.mapper.AppointmentRequestMapper;
import com.fclinic.appointmentservice.application.AppointmentActor;
import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.port.in.*;
import com.fclinic.appointmentservice.application.result.AppointmentOperationResult;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.domain.vo.BusinessId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/appointments")
public class AppointmentController {
  private final BookAppointmentUseCase booking;
  private final CancelAppointmentUseCase cancellation;
  private final RescheduleAppointmentUseCase rescheduling;
  private final GetAppointmentUseCase detail;
  private final ListAppointmentsUseCase listing;
  private final GetAvailableSlotsUseCase availability;
  private final GetAppointmentHistoryUseCase history;
  private final GetAppointmentReferenceUseCase references;

  public AppointmentController(
      BookAppointmentUseCase booking,
      CancelAppointmentUseCase cancellation,
      RescheduleAppointmentUseCase rescheduling,
      GetAppointmentUseCase detail,
      ListAppointmentsUseCase listing,
      GetAvailableSlotsUseCase availability,
      GetAppointmentHistoryUseCase history,
      GetAppointmentReferenceUseCase references) {
    this.booking = booking;
    this.cancellation = cancellation;
    this.rescheduling = rescheduling;
    this.detail = detail;
    this.listing = listing;
    this.availability = availability;
    this.history = history;
    this.references = references;
  }

  @PostMapping
  public ResponseEntity<String> bookAppointment(
      @RequestAttribute("appointmentActor") AppointmentActor actor,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody BookAppointmentRequest body) {
    return result(
        booking.bookAppointment(
            new BookAppointmentCommand(
                actor,
                AppointmentRequestMapper.key(key),
                UUID.fromString(body.patientId()),
                BusinessId.require(body.doctorId()),
                BusinessId.require(body.slotId()),
                body.reasonForVisit())));
  }

  @PutMapping("/{id}/cancel")
  public ResponseEntity<String> cancelAppointment(
      @RequestAttribute("appointmentActor") AppointmentActor actor,
      @RequestHeader("Idempotency-Key") String key,
      @PathVariable UUID id,
      @Valid @RequestBody CancelAppointmentRequest body) {
    return result(
        cancellation.cancelAppointment(
            new CancelAppointmentCommand(
                actor,
                AppointmentRequestMapper.key(key),
                BusinessId.require(id),
                body.version(),
                body.reason())));
  }

  @PutMapping("/{id}/reschedule")
  public ResponseEntity<String> rescheduleAppointment(
      @RequestAttribute("appointmentActor") AppointmentActor actor,
      @RequestHeader("Idempotency-Key") String key,
      @PathVariable UUID id,
      @Valid @RequestBody RescheduleAppointmentRequest body) {
    return result(
        rescheduling.rescheduleAppointment(
            new RescheduleAppointmentCommand(
                actor,
                AppointmentRequestMapper.key(key),
                BusinessId.require(id),
                body.version(),
                BusinessId.require(body.slotId()),
                body.reason())));
  }

  @GetMapping("/{id}")
  public Object getAppointmentById(@RequestAttribute("appointmentActor") AppointmentActor actor, @PathVariable UUID id) {
    return wrap(AppointmentResponse.from(detail.getById(actor, BusinessId.require(id))));
  }

  @GetMapping
  public Object getAppointments(
      @RequestAttribute("appointmentActor") AppointmentActor actor,
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) UUID doctorId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam Map<String, String> params) {
    if (!Set.of("patientId", "doctorId", "date", "status", "page", "size")
        .containsAll(params.keySet()))
      throw AppointmentError.validation("Tham số truy vấn không được hỗ trợ.");
    return wrap(
        AppointmentPageResponse.from(
            listing.listAppointments(actor,
                new AppointmentPageQuery(
                    patientId == null ? null : BusinessId.require(patientId),
                    doctorId == null ? null : BusinessId.require(doctorId), date, status, page, size))));
  }

  @GetMapping("/available-slots")
  public Object availableSlots(
      @RequestAttribute("appointmentActor") AppointmentActor actor,
      @RequestParam UUID doctorId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return wrap(AvailabilityResponse.from(availability.getAvailableSlots(actor, BusinessId.require(doctorId), date)));
  }

  @GetMapping("/{id}/history")
  public Object getHistory(@RequestAttribute("appointmentActor") AppointmentActor actor, @PathVariable UUID id) {
    return wrap(history.getHistory(actor, BusinessId.require(id)).stream().map(AppointmentHistoryResponse::from).toList());
  }

  @GetMapping("/{id}/medical-reference")
  public Object medicalReference(@RequestAttribute("appointmentActor") AppointmentActor actor, @PathVariable UUID id) {
    return wrap(references.medicalReference(actor, BusinessId.require(id)));
  }

  @GetMapping("/{id}/reminder-reference")
  public Object reminderReference(@RequestAttribute("appointmentActor") AppointmentActor actor, @PathVariable UUID id) {
    return wrap(ReminderReferenceResponse.from(references.reminderReference(actor, BusinessId.require(id))));
  }

  private Object wrap(Object data) {
    return Map.of("status", 200, "message", "Thành công", "data", data);
  }

  private ResponseEntity<String> result(AppointmentOperationResult r) {
    return ResponseEntity.status(r.status())
        .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
        .body(r.body());
  }

}
