package com.fclinic.appointmentservice.domain.repository;

import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import java.time.*;
import java.util.*;

public interface AppointmentRepository {
  Appointment save(Appointment appointment);

  Optional<Appointment> findById(Long id);

  Optional<Appointment> findByIdForUpdate(Long id);

  Set<LocalTime> occupied(long doctorId, LocalDate date);

  List<Long> reminderCandidates(Instant now, Duration lead);
}
