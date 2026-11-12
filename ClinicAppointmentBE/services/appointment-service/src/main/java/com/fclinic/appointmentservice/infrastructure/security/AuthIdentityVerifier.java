package com.fclinic.appointmentservice.infrastructure.security;

import static com.fclinic.appointmentservice.infrastructure.client.config.FeignHeaderGuards.*;
import static com.fclinic.appointmentservice.infrastructure.client.config.WireValues.*;
import com.fclinic.appointmentservice.application.AppointmentActor;
import com.fclinic.appointmentservice.config.AppointmentSecuritySettings;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.infrastructure.client.auth.AuthIntrospectClient;
import com.fclinic.appointmentservice.infrastructure.client.config.FeignCalls;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AuthIdentityVerifier {
  private final AuthIntrospectClient client;
  private final AppointmentSecuritySettings settings;
  private final Clock clock;
  // Defer the Feign child context until the MVC security interceptor has finished bootstrapping.
  public AuthIdentityVerifier(@org.springframework.context.annotation.Lazy AuthIntrospectClient client,
      AppointmentSecuritySettings settings, Clock clock) {
    this.client = client; this.settings = settings; this.clock = clock;
  }
  public AppointmentActor verify(String header, long deadline) {
    if (header == null || !header.matches("(?i)Bearer [A-Za-z0-9._~+/-]{1,8192}={0,2}")) throw unauthorized();
    return FeignCalls.call(() -> {
      var n = client.introspect(settings.introspectionUri(),
          "token=" + encode(header.substring(7)) + "&token_type_hint=access_token", FeignCalls.options(deadline));
      require(n != null && n.active() != null);
      if (!n.active()) throw unauthorized();
      require("patient-identity-introspection/2".equals(n.contractVersion()));
      if (settings.issuer() == null || !settings.issuer().equals(n.iss())) throw unauthorized();
      Set<String> audiences = strings(n.aud(), 16);
      if (!audiences.contains(settings.audience()) || n.exp() == null || n.exp() <= clock.instant().getEpochSecond())
        throw unauthorized();
      String subject = text(n.sub(), 255);
      if (!"USER".equals(n.principalType()))
        throw new AppointmentError(403, "FORBIDDEN", "Credential dịch vụ không được dùng cho API người dùng.");
      id(n.userId());
      Set<String> roles = strings(n.authorities(), 32);
      require(Set.of("ROLE_PATIENT", "ROLE_STAFF", "ROLE_DOCTOR", "ROLE_ADMIN").containsAll(roles));
      return new AppointmentActor(subject, "USER", roles, n.patientId() == null ? null : id(n.patientId()),
          n.doctorId() == null ? null : id(n.doctorId()), null, deadline);
    });
  }
  public static AppointmentError unauthorized() { return new AppointmentError(401, "UNAUTHENTICATED", "Token không hợp lệ hoặc đã hết hạn."); }
}
