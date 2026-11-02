package com.fclinic.appointmentservice.domain;

import java.util.UUID;

import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import java.time.*;

public record Slot(
    UUID id, UUID doctorId, LocalDate date, LocalTime start, LocalTime end, boolean published) {
  public boolean valid() {
    return published
        && id != null
        && doctorId != null
        && start.getMinute() == 0
        && start.getSecond() == 0
        && start.getNano() == 0
        && end.isAfter(start)
        && Duration.between(start, end).equals(Duration.ofHours(1));
  }

  public void requireBookable(UUID expectedDoctor, Clock clock) {
    if (!java.util.Objects.equals(doctorId, expectedDoctor)
        || !valid()
        || !date.atTime(start).atZone(Appointment.CLINIC).toInstant().isAfter(clock.instant()))
      throw AppointmentError.conflict(
          "SLOT_NOT_BOOKABLE", "Khung giờ không còn phù hợp để đặt lịch.");
  }
}
