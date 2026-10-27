package com.fclinic.appointmentservice.application.usecase;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.exception.*;
import com.fclinic.appointmentservice.application.port.in.*;
import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.domain.event.*;
import com.fclinic.appointmentservice.domain.model.AppointmentStatus;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PublishOutboxService implements PublishOutboxUseCase {
  private final AppointmentRepositoryPort repository;
  private final OutboxStorePort outbox;
  private final NotificationPayloadPort payloads;
  private final NotificationBrokerPort broker;
  private final com.fclinic.appointmentservice.application.AppointmentSchedulingPolicy policy;
  private final Clock clock;

  public PublishOutboxService(
      AppointmentRepositoryPort repository,
      OutboxStorePort outbox,
      NotificationPayloadPort payloads,
      NotificationBrokerPort broker,
      com.fclinic.appointmentservice.application.AppointmentSchedulingPolicy policy,
      Clock clock) {
    this.repository = repository;
    this.outbox = outbox;
    this.payloads = payloads;
    this.broker = broker;
    this.policy = policy;
    this.clock = clock;
  }

  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(PublishOutboxService.class);

  public void publishPendingEvents() {
    if (!policy.outboxEnabled()) return;
    for (var row : outbox.claim(clock.instant()))
      try {
        if (!currentReminder(row)) {
          outbox.suppress(row, clock.instant());
          continue;
        }
        String payload = row.payload();
        if (payload == null) {
          payload = payloads.resolvePayload(row);
          outbox.ready(row, payload, clock.instant());
        }
        if (!currentReminder(row)) {
          outbox.suppress(row, clock.instant());
          continue;
        }
        broker.publish(row, payload);
        outbox.published(row, clock.instant());
      } catch (Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        outbox.retry(
            row,
            clock.instant(),
            e instanceof AppointmentError ? "DEPENDENCY_UNAVAILABLE" : "BROKER_UNAVAILABLE");
        LOG.warn("Outbox retry eventId={} type={}", row.id(), row.eventType());
      }
  }

  private boolean currentReminder(OutboxMessage row) {
    if (!row.eventType().equals("APPOINTMENT_REMINDER")) return true;
    var found = repository.findById(row.appointmentId());
    if (found.isEmpty()) return false;
    var a = found.get();
    var reminder = row.reminder();
    return a.getStatus() == AppointmentStatus.CONFIRMED
        && a.getVersion() == row.aggregateVersion()
        && a.startInstant().isAfter(clock.instant())
        && a.getSlotId() == reminder.slotId()
        && a.getAppointmentDate().equals(reminder.date())
        && a.getStartTime().equals(reminder.start())
        && a.getEndTime().equals(reminder.end());
  }
}
