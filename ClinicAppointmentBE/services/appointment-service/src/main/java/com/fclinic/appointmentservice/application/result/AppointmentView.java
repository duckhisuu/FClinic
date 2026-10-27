package com.fclinic.appointmentservice.application.result;

import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record AppointmentView(
    Long id,
    String bookingCode,
    UUID patientId,
    String patientName,
    String patientPhone,
    Long doctorId,
    String doctorName,
    String doctorSpecialty,
    Long slotId,
    LocalDate appointmentDate,
    LocalTime startTime,
    LocalTime endTime,
    String reasonForVisit,
    BigDecimal consultationFee,
    String status,
    long version,
    Instant createdAt,
    Instant updatedAt) {
  public static AppointmentView from(Appointment d) {
    return new AppointmentView(
        d.getId(),
        d.getBookingCode().value(),
        d.getPatientId(),
        d.getPatientName(),
        d.getPatientPhone(),
        d.getDoctorId(),
        d.getDoctorName(),
        d.getDoctorSpecialty(),
        d.getSlotId(),
        d.getAppointmentDate(),
        d.getStartTime(),
        d.getEndTime(),
        d.getReasonForVisit(),
        d.getConsultationFee(),
        d.getStatus().name(),
        d.getVersion(),
        d.getCreatedAt(),
        d.getUpdatedAt());
  }
}
