package com.fclinic.appointmentservice.application.usecase;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.exception.*;
import com.fclinic.appointmentservice.application.port.in.*;
import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import com.fclinic.appointmentservice.domain.event.*;
import com.fclinic.appointmentservice.application.AppointmentActor;
import com.fclinic.appointmentservice.application.security.AppointmentPolicy;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GetAvailableSlotsService implements GetAvailableSlotsUseCase {
  private final AppointmentRepositoryPort repository;
  private final DoctorReferencePort doctors;
  private final Clock clock;
  private final AppointmentPolicy policy;

  public GetAvailableSlotsService(
      AppointmentRepositoryPort repository, DoctorReferencePort doctors, Clock clock, AppointmentPolicy policy) {
    this.repository = repository;
    this.doctors = doctors;
    this.clock = clock;
    this.policy = policy;
  }

  public AvailabilityView getAvailableSlots(AppointmentActor actor, UUID doctorId, LocalDate date) {
    policy.availability(actor, doctorId);
    long deadline = actor.deadlineNanos();
    doctors.doctor(doctorId, deadline);
    var offered = doctors.offered(doctorId, date, deadline);
    var occupied = repository.occupied(doctorId, date);
    var slots =
        offered.stream()
            .filter(com.fclinic.appointmentservice.domain.Slot::valid)
            .map(
                s ->
                    new SlotView(
                        s.id(),
                        doctorId,
                        s.date(),
                        s.start(),
                        s.end(),
                        !occupied.contains(s.start())
                            && s.date()
                                .atTime(s.start())
                                .atZone(Appointment.CLINIC)
                                .toInstant()
                                .isAfter(clock.instant())))
            .sorted(Comparator.comparing(SlotView::startTime))
            .toList();
    return new AvailabilityView(doctorId, date, "Asia/Saigon", slots);
  }
}
