package com.fclinic.appointmentservice.api;

import com.fclinic.appointmentservice.application.AppointmentActor;
import com.fclinic.appointmentservice.config.AppointmentSecuritySettings;
import com.fclinic.appointmentservice.infrastructure.security.AuthIdentityVerifier;
import jakarta.servlet.http.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AppointmentAuthentication implements HandlerInterceptor {
  private final AuthIdentityVerifier identities;
  private final AppointmentSecuritySettings settings;
  public AppointmentAuthentication(AuthIdentityVerifier identities, AppointmentSecuritySettings settings) {
    this.identities = identities; this.settings = settings;
  }
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    var headers = Collections.list(request.getHeaders("Authorization"));
    if (headers.size() != 1) throw AuthIdentityVerifier.unauthorized();
    String path = request.getRequestURI();
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    AppointmentActor actor;
    if (path.matches("/api/v2/appointments/[^/]+/medical-reference")) {
      authenticateBasic(headers.getFirst(), settings.patientClientId(), settings.patientClientSecret());
      actor = new AppointmentActor("service:patient-service", "SERVICE", Set.of("SVC_APPOINTMENT_READ"), null, null, "patient-service", deadline);
    } else if (path.matches("/api/v2/appointments/[^/]+/reminder-reference")) {
      authenticateBasic(headers.getFirst(), settings.notificationClientId(), settings.notificationClientSecret());
      actor = new AppointmentActor("service:notification-service", "SERVICE", Set.of("SVC_APPOINTMENT_REMINDER_READ"), null, null, "notification-service", deadline);
    } else actor = identities.verify(headers.getFirst(), deadline);
    request.setAttribute("appointmentActor", actor);
    response.setHeader("Cache-Control", "no-store");
    return true;
  }
  public static void authenticateBasic(String header, String id, String secret) {
    boolean valid = false;
    if (id != null && !id.isBlank() && secret != null && !secret.isBlank()
        && header.regionMatches(true, 0, "Basic ", 0, 6) && header.length() <= 8192) {
      try {
        String decoded = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
        int split = decoded.indexOf(':');
        if (split > 0) {
          String givenId = URLDecoder.decode(decoded.substring(0, split), StandardCharsets.UTF_8);
          String givenSecret = URLDecoder.decode(decoded.substring(split + 1), StandardCharsets.UTF_8);
          valid = MessageDigest.isEqual(id.getBytes(StandardCharsets.UTF_8), givenId.getBytes(StandardCharsets.UTF_8))
              & MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), givenSecret.getBytes(StandardCharsets.UTF_8));
        }
      } catch (IllegalArgumentException ignored) { }
    }
    if (!valid) throw AuthIdentityVerifier.unauthorized();
  }
}
