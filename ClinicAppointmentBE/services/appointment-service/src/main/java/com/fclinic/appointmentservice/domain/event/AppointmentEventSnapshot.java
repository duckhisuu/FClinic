package com.fclinic.appointmentservice.domain.event;

import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import java.time.*;
import java.util.*;

public record AppointmentEventSnapshot(
    UUID id,
    String bookingCode,
    UUID patientId,
    String patientName,
    String patientPhone,
    UUID doctorId,
    String doctorName,
    String doctorSpecialty,
    UUID slotId,
    LocalDate appointmentDate,
    LocalTime startTime,
    LocalTime endTime,
    long version) {
  public static AppointmentEventSnapshot from(Appointment a) {
    return new AppointmentEventSnapshot(
        a.getId(),
        a.getBookingCode().value(),
        a.getPatientId(),
        a.getPatientName(),
        a.getPatientPhone(),
        a.getDoctorId(),
        a.getDoctorName(),
        a.getDoctorSpecialty(),
        a.getSlotId(),
        a.getAppointmentDate(),
        a.getStartTime(),
        a.getEndTime(),
        a.getVersion());
  }
}
