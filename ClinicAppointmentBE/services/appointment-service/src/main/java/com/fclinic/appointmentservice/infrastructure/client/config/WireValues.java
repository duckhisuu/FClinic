package com.fclinic.appointmentservice.infrastructure.client.config;

import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.domain.vo.BusinessId;
import java.util.*;

public final class WireValues {
  private WireValues() {}
  public static UUID id(String value) {
    if (value == null || !value.matches(BusinessId.PATTERN)) throw AppointmentError.unavailable();
    return UUID.fromString(value);
  }
  public static String text(String value, int maximum) {
    FeignHeaderGuards.require(value != null && !value.isBlank() && value.length() <= maximum
        && value.chars().noneMatch(Character::isISOControl));
    return value;
  }
  public static String email(String value) {
    if (value == null) return null;
    FeignHeaderGuards.require(text(value, 320).matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"));
    return value;
  }
  public static Set<String> strings(List<String> values, int maximum) {
    FeignHeaderGuards.require(values != null && !values.isEmpty() && values.size() <= maximum);
    Set<String> result = new HashSet<>();
    for (String value : values)
      FeignHeaderGuards.require(value != null && !value.isBlank() && value.length() <= 128 && result.add(value));
    return Set.copyOf(result);
  }
}
