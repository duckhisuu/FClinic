package com.fclinic.appointmentservice.api.dto;

import static com.fclinic.appointmentservice.api.mapper.AppointmentWireFormat.*;

import com.fclinic.appointmentservice.application.result.AppointmentView;
import java.math.BigDecimal;

public record AppointmentResponse(
    Long id,
    String bookingCode,
    String patientId,
    String patientName,
    String patientPhone,
    Long doctorId,
    String doctorName,
    String doctorSpecialty,
    Long slotId,
    String appointmentDate,
    String startTime,
    String endTime,
    String reasonForVisit,
    BigDecimal consultationFee,
    String status,
    long version,
    String createdAt,
    String updatedAt) {
  public static AppointmentResponse from(AppointmentView v) {
    return new AppointmentResponse(
        v.id(),
        v.bookingCode(),
        v.patientId().toString(),
        v.patientName(),
        v.patientPhone(),
        v.doctorId(),
        v.doctorName(),
        v.doctorSpecialty(),
        v.slotId(),
        date(v.appointmentDate()),
        time(v.startTime()),
        time(v.endTime()),
        v.reasonForVisit(),
        v.consultationFee(),
        v.status(),
        v.version(),
        v.createdAt().toString(),
        v.updatedAt().toString());
  }
}
