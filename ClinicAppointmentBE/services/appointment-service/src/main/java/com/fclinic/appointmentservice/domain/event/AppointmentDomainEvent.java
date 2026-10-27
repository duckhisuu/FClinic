package com.fclinic.appointmentservice.domain.event;

import java.time.Instant;
import java.util.UUID;

public interface AppointmentDomainEvent {
  UUID eventId();

  Instant occurredAt();

  AppointmentEventSnapshot snapshot();

  String eventType();
}
