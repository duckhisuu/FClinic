package com.fclinic.appointmentservice.application.usecase;

import static com.fclinic.appointmentservice.application.usecase.IdempotentAppointmentOperation.normalize;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.exception.*;
import com.fclinic.appointmentservice.application.port.in.*;
import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.event.*;
import com.fclinic.appointmentservice.application.security.AppointmentPolicy;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CancelAppointmentService implements CancelAppointmentUseCase {
  private final AppointmentRepositoryPort repository;
  private final UnitOfWorkPort transactions;
  private final AppointmentHistoryPort history;
  private final AppointmentEventPublisherPort events;
  private final IdempotentAppointmentOperation operations;
  private final Clock clock;
  private final AppointmentPolicy policy;

  public CancelAppointmentService(
      AppointmentRepositoryPort repository,
      UnitOfWorkPort transactions,
      AppointmentHistoryPort history,
      AppointmentEventPublisherPort events,
      IdempotentAppointmentOperation operations,
      Clock clock, AppointmentPolicy policy) {
    this.repository = repository;
    this.transactions = transactions;
    this.history = history;
    this.events = events;
    this.operations = operations;
    this.clock = clock;
    this.policy = policy;
  }

  public AppointmentOperationResult cancelAppointment(CancelAppointmentCommand command) {
    policy.authenticated(command.actor());
    var original = repository.findById(command.appointmentId()).orElseThrow(AppointmentNotFoundException::new);
    policy.mutate(command.actor(), original.getPatientId(), "APPOINTMENT_CANCEL");
    Map<String, Object> body = new TreeMap<>();
    body.put("version", command.version());
    body.put("reason", normalize(command.reason()));
    return operations.execute(
        command.actor(),
        command.idempotencyKey(),
        "CANCEL",
        "PUT /api/v2/appointments/" + command.appointmentId() + "/cancel",
        body,
        attempt ->
            transactions.execute(
                () -> {
                  var current =
                      repository
                          .findByIdForUpdate(command.appointmentId())
                          .orElseThrow(AppointmentNotFoundException::new);
                  var previous = current.copy();
                  current.cancel(command.version(), clock);
                  var saved = repository.save(current);
                  Instant now = clock.instant();
                  history.record(
                      saved,
                      previous,
                      "CANCELLED",
                      normalize(command.reason()),
                      command.actor().subject(),
                      command.actor().auditRole(),
                      now);
                  events.publish(
                      new AppointmentCancelledDomainEvent(
                          UUID.randomUUID(), now, AppointmentEventSnapshot.from(saved)));
                  return operations.complete(attempt, AppointmentView.from(saved), 200, now);
                }));
  }
}
