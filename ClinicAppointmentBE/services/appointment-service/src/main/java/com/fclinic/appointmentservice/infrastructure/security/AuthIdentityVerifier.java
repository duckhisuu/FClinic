package com.fclinic.appointmentservice.infrastructure.security;

import com.fclinic.appointmentservice.application.AppointmentActor;
import com.fclinic.appointmentservice.config.AppointmentSecuritySettings;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.domain.vo.BusinessId;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class AuthIdentityVerifier {
  private final AuthHttpTransport http;
  private final AppointmentSecuritySettings settings;
  private final Clock clock;
  public AuthIdentityVerifier(AuthHttpTransport http, AppointmentSecuritySettings settings, Clock clock) {
    this.http = http; this.settings = settings; this.clock = clock;
  }
  public AppointmentActor verify(String header, long deadline) {
    if (header == null || !header.matches("(?i)Bearer [A-Za-z0-9._~+/-]{1,8192}={0,2}")) throw unauthorized();
    var n = http.post(settings.introspectionUri(), settings.clientId(), settings.clientSecret(),
        "application/x-www-form-urlencoded", "token=" + AuthHttpTransport.encode(header.substring(7))
            + "&token_type_hint=access_token", deadline);
    try {
      check(n.path("active").isBoolean());
      if (!n.path("active").asBoolean()) { check(n.size() == 1); throw unauthorized(); }
      check(n.propertyNames().equals(Set.of("active", "contractVersion", "iss", "aud", "exp", "sub",
          "principalType", "userId", "patientId", "doctorId", "authorities")));
      check("patient-identity-introspection/2".equals(n.path("contractVersion").asText()));
      if (settings.issuer() == null || !settings.issuer().equals(n.path("iss").asText())) throw unauthorized();
      Set<String> audiences = strings(n.path("aud"), 16);
      if (!audiences.contains(settings.audience()) || !n.path("exp").isIntegralNumber()
          || !n.path("exp").canConvertToLong() || n.path("exp").longValue() <= clock.instant().getEpochSecond())
        throw unauthorized();
      String subject = n.path("sub").asText("");
      check(!subject.isBlank() && subject.length() <= 255 && subject.chars().noneMatch(Character::isISOControl));
      // Public appointment APIs require a human. Machine credentials have dedicated projections.
      if (!"USER".equals(n.path("principalType").asText()))
        throw new AppointmentError(403, "FORBIDDEN", "Credential dịch vụ không được dùng cho API người dùng.");
      BusinessId.parse(n.path("userId").asText());
      Set<String> roles = strings(n.path("authorities"), 32);
      check(Set.of("ROLE_PATIENT", "ROLE_STAFF", "ROLE_DOCTOR", "ROLE_ADMIN").containsAll(roles));
      return new AppointmentActor(subject, "USER", roles, nullableId(n.path("patientId")),
          nullableId(n.path("doctorId")), null, deadline);
    } catch (AppointmentError e) {
      if (e.status() == 400) throw AppointmentError.unavailable();
      throw e;
    } catch (RuntimeException e) { throw AppointmentError.unavailable(); }
  }
  private static UUID nullableId(JsonNode n) { return n.isNull() ? null : BusinessId.parse(n.asText()); }
  private static Set<String> strings(JsonNode n, int maximum) {
    check(n.isArray() && n.size() > 0 && n.size() <= maximum);
    Set<String> values = new HashSet<>();
    for (var v : n) check(v.isString() && !v.asText().isBlank() && v.asText().length() <= 128 && values.add(v.asText()));
    return Set.copyOf(values);
  }
  private static void check(boolean ok) { if (!ok) throw AppointmentError.unavailable(); }
  public static AppointmentError unauthorized() { return new AppointmentError(401, "UNAUTHENTICATED", "Token không hợp lệ hoặc đã hết hạn."); }
}
