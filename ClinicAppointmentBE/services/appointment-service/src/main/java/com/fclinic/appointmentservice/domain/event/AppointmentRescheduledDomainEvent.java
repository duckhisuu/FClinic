package com.fclinic.appointmentservice.domain.event;

import java.time.*;
import java.util.UUID;

public record AppointmentRescheduledDomainEvent(
    UUID eventId,
    Instant occurredAt,
    AppointmentEventSnapshot snapshot,
    long previousSlotId,
    LocalDate previousAppointmentDate,
    LocalTime previousStartTime,
    LocalTime previousEndTime)
    implements AppointmentDomainEvent {
  public String eventType() {
    return "APPOINTMENT_RESCHEDULED";
  }
}
