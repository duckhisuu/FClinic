package com.fclinic.appointmentservice.domain;

import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import java.time.*;

public record Slot(
    long id, long doctorId, LocalDate date, LocalTime start, LocalTime end, boolean published) {
  public boolean valid() {
    return published
        && id > 0
        && doctorId > 0
        && start.getMinute() == 0
        && start.getSecond() == 0
        && start.getNano() == 0
        && end.isAfter(start)
        && Duration.between(start, end).equals(Duration.ofHours(1));
  }

  public void requireBookable(long expectedDoctor, Clock clock) {
    if (doctorId != expectedDoctor
        || !valid()
        || !date.atTime(start).atZone(Appointment.CLINIC).toInstant().isAfter(clock.instant()))
      throw AppointmentError.conflict(
          "SLOT_NOT_BOOKABLE", "Khung giờ không còn phù hợp để đặt lịch.");
  }
}
