package com.fclinic.appointmentservice.domain.event;

import java.time.*;
import java.util.UUID;

public record AppointmentBookedDomainEvent(
    UUID eventId, Instant occurredAt, AppointmentEventSnapshot snapshot)
    implements AppointmentDomainEvent {
  public String eventType() {
    return "APPOINTMENT_CREATED";
  }
}
