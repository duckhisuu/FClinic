package com.fclinic.appointmentservice.api.dto;

import java.util.UUID;

import static com.fclinic.appointmentservice.api.mapper.AppointmentWireFormat.*;

import com.fclinic.appointmentservice.application.result.ReminderReferenceView;

public record ReminderReferenceResponse(
    UUID id,
    String patientId,
    UUID doctorId,
    String status,
    long version,
    UUID slotId,
    String appointmentDate,
    String startTime,
    String endTime,
    String timezone) {
  public static ReminderReferenceResponse from(ReminderReferenceView v) {
    return new ReminderReferenceResponse(
        v.id(),
        v.patientId().toString(),
        v.doctorId(),
        v.status(),
        v.version(),
        v.slotId(),
        date(v.appointmentDate()),
        time(v.startTime()),
        time(v.endTime()),
        v.timezone());
  }
}
