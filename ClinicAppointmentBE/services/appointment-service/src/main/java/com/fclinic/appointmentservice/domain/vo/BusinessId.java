package com.fclinic.appointmentservice.domain.vo;

import com.fclinic.appointmentservice.domain.AppointmentError;
import java.util.UUID;

/** Business identifiers shared with Patient are RFC 4122 UUIDv4. Correlation keys are separate. */
public final class BusinessId {
  public static final String PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

  private BusinessId() {}

  public static boolean valid(UUID value) {
    return value != null && value.version() == 4 && value.variant() == 2;
  }

  public static UUID require(UUID value) {
    if (!valid(value)) throw AppointmentError.validation("Định danh nghiệp vụ phải là UUIDv4 hợp lệ.");
    return value;
  }
}
