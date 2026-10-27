package com.fclinic.appointmentservice.application.usecase;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.exception.*;
import com.fclinic.appointmentservice.application.port.in.*;
import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.event.*;
import com.fclinic.appointmentservice.domain.model.AppointmentStatus;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GenerateAppointmentRemindersService implements GenerateAppointmentRemindersUseCase {
  private final AppointmentRepositoryPort repository;
  private final OutboxStorePort outbox;
  private final AppointmentEventPublisherPort events;
  private final UnitOfWorkPort transactions;
  private final com.fclinic.appointmentservice.application.AppointmentSchedulingPolicy policy;
  private final Clock clock;

  public GenerateAppointmentRemindersService(
      AppointmentRepositoryPort repository,
      OutboxStorePort outbox,
      AppointmentEventPublisherPort events,
      UnitOfWorkPort transactions,
      com.fclinic.appointmentservice.application.AppointmentSchedulingPolicy policy,
      Clock clock) {
    this.repository = repository;
    this.outbox = outbox;
    this.events = events;
    this.transactions = transactions;
    this.policy = policy;
    this.clock = clock;
  }

  public void generateDueReminders() {
    Duration lead = policy.reminderLead();
    if (lead == null) return;
    for (long id : repository.reminderCandidates(clock.instant(), lead))
      transactions.execute(
          () -> {
            var a = repository.findByIdForUpdate(id).orElseThrow(AppointmentNotFoundException::new);
            Instant now = clock.instant(), due = a.startInstant().minus(lead);
            if (a.getStatus() == AppointmentStatus.CONFIRMED
                && a.startInstant().isAfter(now)
                && !due.isAfter(now)
                && !outbox.reminderExists(a, due))
              events.publish(
                  new AppointmentReminderDomainEvent(
                      UUID.randomUUID(), now, AppointmentEventSnapshot.from(a), due));
            return null;
          });
  }
}
