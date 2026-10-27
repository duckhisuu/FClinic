package com.fclinic.appointmentservice.domain.vo;

import java.util.Objects;
import java.util.UUID;

public record BookingCode(String value) {

  public BookingCode {
    Objects.requireNonNull(value, "Booking code must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("Booking code must not be empty");
    }
  }

  public static BookingCode generate() {
    return new BookingCode(
        "FC-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT));
  }

  public static BookingCode of(String value) {
    return new BookingCode(value);
  }
}
