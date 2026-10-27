package com.fclinic.appointmentservice.api.mapper;

import java.time.*;
import java.time.format.DateTimeFormatter;

public final class AppointmentWireFormat {
  private AppointmentWireFormat() {}

  public static String time(LocalTime value) {
    return value == null ? null : value.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
  }

  public static String date(LocalDate value) {
    return value == null ? null : value.toString();
  }
}
