package com.fclinic.appointmentservice.domain.event;

import java.time.*;
import java.util.UUID;

public record AppointmentReminderDomainEvent(
    UUID eventId, Instant occurredAt, AppointmentEventSnapshot snapshot, Instant reminderDueAt)
    implements AppointmentDomainEvent {
  public String eventType() {
    return "APPOINTMENT_REMINDER";
  }
}
