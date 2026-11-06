package com.fclinic.appointmentservice.infrastructure;

import java.util.UUID;

import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.domain.vo.BusinessId;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

public final class AppointmentJson {
  public static final JsonMapper JSON =
      JsonMapper.builder(
              JsonFactory.builder()
                  .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                  .streamReadConstraints(
                      StreamReadConstraints.builder()
                          .maxNestingDepth(24)
                          .maxStringLength(262144)
                          .build())
                  .build())
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  private AppointmentJson() {}

  public static String write(Object value) {
    return JSON.writeValueAsString(value);
  }

  public static JsonNode read(String s) {
    try {
      return JSON.readTree(s);
    } catch (RuntimeException e) {
      throw AppointmentError.unavailable();
    }
  }

  public static UUID id(JsonNode n) {
    if (n == null || !n.isString()
        || !n.asText().matches(BusinessId.PATTERN))
      throw AppointmentError.unavailable();
    return UUID.fromString(n.asText());
  }

  public static String text(JsonNode n, int max) {
    if (n == null
        || !n.isString()
        || n.asText().isBlank()
        || n.asText().length() > max
        || n.asText().chars().anyMatch(Character::isISOControl))
      throw AppointmentError.unavailable();
    return n.asText();
  }

  public static void require(boolean condition) {
    if (!condition) throw AppointmentError.unavailable();
  }
}
