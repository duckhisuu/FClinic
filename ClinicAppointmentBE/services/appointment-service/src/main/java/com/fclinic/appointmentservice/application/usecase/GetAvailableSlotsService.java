package com.fclinic.appointmentservice.application.usecase;

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
public class GetAvailableSlotsService implements GetAvailableSlotsUseCase {
  private final AppointmentRepositoryPort repository;
  private final DoctorReferencePort doctors;
  private final Clock clock;

  public GetAvailableSlotsService(
      AppointmentRepositoryPort repository, DoctorReferencePort doctors, Clock clock) {
    this.repository = repository;
    this.doctors = doctors;
    this.clock = clock;
  }

  public AvailabilityView getAvailableSlots(long doctorId, LocalDate date) {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
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
