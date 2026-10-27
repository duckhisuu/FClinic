package com.fclinic.appointmentservice.application.usecase;

import static com.fclinic.appointmentservice.application.usecase.IdempotentAppointmentOperation.normalize;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.exception.*;
import com.fclinic.appointmentservice.application.port.in.*;
import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import com.fclinic.appointmentservice.domain.event.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AppointmentApplicationService implements BookAppointmentUseCase {
  private final AppointmentRepositoryPort repository;
  private final UnitOfWorkPort transactions;
  private final AppointmentHistoryPort history;
  private final AppointmentEventPublisherPort events;
  private final IdempotentAppointmentOperation operations;
  private final Clock clock;
  private final PatientReferencePort patients;
  private final DoctorReferencePort doctors;

  public AppointmentApplicationService(
      AppointmentRepositoryPort repository,
      UnitOfWorkPort transactions,
      AppointmentHistoryPort history,
      AppointmentEventPublisherPort events,
      IdempotentAppointmentOperation operations,
      Clock clock,
      PatientReferencePort patients,
      DoctorReferencePort doctors) {
    this.repository = repository;
    this.transactions = transactions;
    this.history = history;
    this.events = events;
    this.operations = operations;
    this.clock = clock;
    this.patients = patients;
    this.doctors = doctors;
  }

  /** Original application service is retained as the booking use-case implementation. */
  public AppointmentOperationResult bookAppointment(BookAppointmentCommand command) {
    String reason = normalize(command.reasonForVisit());
    Map<String, Object> body = new TreeMap<>();
    body.put("patientId", command.patientId().toString());
    body.put("doctorId", command.doctorId());
    body.put("slotId", command.slotId());
    body.put("reasonForVisit", reason);
    return operations.execute(
        command.actor(),
        command.idempotencyKey(),
        "CREATE",
        "POST /api/v2/appointments",
        body,
        attempt -> {
          // All remote references are resolved before opening the local write transaction.
          var patient = patients.patient(command.patientId(), command.actor().deadlineNanos());
          var doctor = doctors.doctor(command.doctorId(), command.actor().deadlineNanos());
          var slot = doctors.slot(doctor.id(), command.slotId(), command.actor().deadlineNanos());
          slot.requireBookable(doctor.id(), clock);
          return transactions.execute(
              () -> {
                var current =
                    Appointment.createNew(
                        patient.id(),
                        patient.name(),
                        patient.phone(),
                        doctor.id(),
                        doctor.name(),
                        doctor.specialty(),
                        slot,
                        reason,
                        doctor.fee(),
                        clock);
                var saved = repository.save(current);
                Instant now = clock.instant();
                history.record(
                    saved, null, "CREATED", null, command.actor().subject(), "SYSTEM", now);
                events.publish(
                    new AppointmentBookedDomainEvent(
                        UUID.randomUUID(), now, AppointmentEventSnapshot.from(saved)));
                return operations.complete(attempt, AppointmentView.from(saved), 201, now);
              });
        });
  }
}
