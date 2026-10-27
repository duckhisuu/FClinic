package com.fclinic.appointmentservice.api.dto;

import static com.fclinic.appointmentservice.api.mapper.AppointmentWireFormat.*;

import com.fclinic.appointmentservice.application.result.ReminderReferenceView;

public record ReminderReferenceResponse(
    long id,
    String patientId,
    long doctorId,
    String status,
    long version,
    long slotId,
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
