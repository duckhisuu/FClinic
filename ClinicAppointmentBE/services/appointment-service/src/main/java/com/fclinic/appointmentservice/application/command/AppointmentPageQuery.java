package com.fclinic.appointmentservice.application.command;

import java.time.*;
import java.util.*;

public record AppointmentPageQuery(
    UUID patientId, UUID doctorId, LocalDate date, String status, int page, int size,
    Set<UUID> allowedPatientIds) {
  public AppointmentPageQuery { if (allowedPatientIds != null) allowedPatientIds = Set.copyOf(allowedPatientIds); }
  public AppointmentPageQuery(UUID patientId, UUID doctorId, LocalDate date, String status, int page, int size) {
    this(patientId, doctorId, date, status, page, size, null);
  }
}
