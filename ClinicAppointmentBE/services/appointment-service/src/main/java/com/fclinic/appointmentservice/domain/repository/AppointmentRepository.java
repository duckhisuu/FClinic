package com.fclinic.appointmentservice.domain.repository;

import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import java.time.*;
import java.util.*;

public interface AppointmentRepository {
  Appointment save(Appointment appointment);

  Optional<Appointment> findById(UUID id);

  Optional<Appointment> findByIdForUpdate(UUID id);

  Set<LocalTime> occupied(UUID doctorId, LocalDate date);

  List<UUID> reminderCandidates(Instant now, Duration lead);
}
