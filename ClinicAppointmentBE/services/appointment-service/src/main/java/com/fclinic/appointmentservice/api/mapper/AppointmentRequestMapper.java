package com.fclinic.appointmentservice.api.mapper;

import com.fclinic.appointmentservice.domain.AppointmentError;
import java.util.UUID;

public final class AppointmentRequestMapper {
  private AppointmentRequestMapper() {}

  public static UUID key(String value) {
    try {
      if (value == null
          || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
        throw new IllegalArgumentException();
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw AppointmentError.validation(
          "Idempotency-Key phải là UUID hợp lệ và được giữ nguyên khi thử lại.");
    }
  }
}
