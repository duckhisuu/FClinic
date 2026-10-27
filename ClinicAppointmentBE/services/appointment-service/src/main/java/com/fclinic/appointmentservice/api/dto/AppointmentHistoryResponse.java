package com.fclinic.appointmentservice.api.dto;

import static com.fclinic.appointmentservice.api.mapper.AppointmentWireFormat.*;

import com.fclinic.appointmentservice.application.result.AppointmentHistoryView;

public record AppointmentHistoryResponse(
    long id,
    String action,
    long version,
    String oldDate,
    String oldStartTime,
    String newDate,
    String newStartTime,
    String reason,
    String actorRole,
    String createdAt) {
  public static AppointmentHistoryResponse from(AppointmentHistoryView v) {
    return new AppointmentHistoryResponse(
        v.id(),
        v.action(),
        v.version(),
        date(v.oldDate()),
        time(v.oldStartTime()),
        date(v.newDate()),
        time(v.newStartTime()),
        v.reason(),
        v.actorRole(),
        v.createdAt().toString());
  }
}
