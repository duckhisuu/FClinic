package com.fclinic.appointmentservice.application.usecase;

import static com.fclinic.appointmentservice.application.usecase.IdempotentAppointmentOperation.normalize;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.exception.*;
import com.fclinic.appointmentservice.application.port.in.*;
import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.event.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RescheduleAppointmentService implements RescheduleAppointmentUseCase {
  private final AppointmentRepositoryPort repository;
  private final UnitOfWorkPort transactions;
  private final AppointmentHistoryPort history;
  private final AppointmentEventPublisherPort events;
  private final IdempotentAppointmentOperation operations;
  private final Clock clock;
  private final DoctorReferencePort doctors;

  public RescheduleAppointmentService(
      AppointmentRepositoryPort repository,
      UnitOfWorkPort transactions,
      AppointmentHistoryPort history,
      AppointmentEventPublisherPort events,
      IdempotentAppointmentOperation operations,
      Clock clock,
      DoctorReferencePort doctors) {
    this.repository = repository;
    this.transactions = transactions;
    this.history = history;
    this.events = events;
    this.operations = operations;
    this.clock = clock;
    this.doctors = doctors;
  }

  public AppointmentOperationResult rescheduleAppointment(RescheduleAppointmentCommand command) {
    Map<String, Object> body = new TreeMap<>();
    body.put("version", command.version());
    body.put("slotId", command.slotId());
    body.put("reason", normalize(command.reason()));
    return operations.execute(
        command.actor(),
        command.idempotencyKey(),
        "RESCHEDULE",
        "PUT /api/v2/appointments/" + command.appointmentId() + "/reschedule",
        body,
        attempt -> {
          var original =
              repository
                  .findById(command.appointmentId())
                  .orElseThrow(AppointmentNotFoundException::new);
          original.requireChange(clock, command.version());
          doctors.doctor(original.getDoctorId(), command.actor().deadlineNanos());
          var destination =
              doctors.slot(
                  original.getDoctorId(), command.slotId(), command.actor().deadlineNanos());
          destination.requireBookable(original.getDoctorId(), clock);
          return transactions.execute(
              () -> {
                var current =
                    repository
                        .findByIdForUpdate(command.appointmentId())
                        .orElseThrow(AppointmentNotFoundException::new);
                var previous = current.copy();
                current.reschedule(destination, command.version(), clock);
                var saved = repository.save(current);
                Instant now = clock.instant();
                history.record(
                    saved,
                    previous,
                    "RESCHEDULED",
                    normalize(command.reason()),
                    command.actor().subject(),
                    "SYSTEM",
                    now);
                events.publish(
                    new AppointmentRescheduledDomainEvent(
                        UUID.randomUUID(),
                        now,
                        AppointmentEventSnapshot.from(saved),
                        previous.getSlotId(),
                        previous.getAppointmentDate(),
                        previous.getStartTime(),
                        previous.getEndTime()));
                return operations.complete(attempt, AppointmentView.from(saved), 200, now);
              });
        });
  }
}
