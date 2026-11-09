package com.fclinic.appointmentservice.infrastructure.security;

import com.fclinic.appointmentservice.application.AppointmentActor;
import com.fclinic.appointmentservice.application.port.out.AppointmentScopePort;
import com.fclinic.appointmentservice.config.AppointmentSecuritySettings;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.domain.vo.BusinessId;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class AuthAppointmentScopeAdapter implements AppointmentScopePort {
  private final AuthHttpTransport http;
  private final AppointmentSecuritySettings settings;
  private final Clock clock;
  public AuthAppointmentScopeAdapter(AuthHttpTransport http, AppointmentSecuritySettings settings, Clock clock) {
    this.http = http; this.settings = settings; this.clock = clock;
  }
  public boolean allowed(AppointmentActor actor, String operation, UUID patientId) {
    return evaluate(actor, operation, patientId).allowed();
  }
  public Set<UUID> patients(AppointmentActor actor) {
    var decision = evaluate(actor, "APPOINTMENT_SEARCH", null);
    if (!decision.allowed()) throw new AppointmentError(403, "FORBIDDEN", "Chưa được cấp phạm vi tra cứu lịch hẹn.");
    return decision.patients();
  }
  private record Decision(boolean allowed, Set<UUID> patients) { }
  private Decision evaluate(AppointmentActor actor, String operation, UUID patientId) {
    Map<String, Object> who = new LinkedHashMap<>();
    who.put("subject", actor.subject()); who.put("principalType", actor.principalType());
    who.put("patientId", actor.patientId()); who.put("doctorId", actor.doctorId()); who.put("serviceName", null);
    Map<String, Object> query = new LinkedHashMap<>();
    Instant requested = clock.instant();
    query.put("contractVersion", "appointment-work-scope/1"); query.put("nonce", UUID.randomUUID());
    query.put("requestedAt", requested); query.put("authority", "ADMINISTRATIVE"); query.put("operation", operation);
    query.put("caller", who); query.put("patientId", patientId); query.put("appointmentId", null); query.put("visitDate", null);
    try {
      String body = AuthHttpTransport.JSON.writeValueAsString(query);
      JsonNode n = http.post(settings.scopeUri(), settings.scopeClientId(), settings.scopeClientSecret(),
          "application/json", body, actor.deadlineNanos());
      require(n.propertyNames().equals(Set.of("contractVersion", "request", "evaluatedAt", "allowed", "patientIds", "evidence"))
          && "appointment-work-scope/1".equals(n.path("contractVersion").asText())
          && n.path("request").equals(AuthHttpTransport.JSON.readTree(body)) && n.path("allowed").isBoolean());
      Instant now = clock.instant(), evaluated = Instant.parse(n.path("evaluatedAt").asText());
      require(!evaluated.isBefore(requested.minusSeconds(1)) && !evaluated.isAfter(now.plusSeconds(1)));
      if (!n.path("allowed").asBoolean()) {
        require(n.path("patientIds").isNull() && n.path("evidence").isNull());
        return new Decision(false, Set.of());
      }
      var proof = n.path("evidence");
      require(proof.isObject() && proof.propertyNames().equals(Set.of("assignmentId", "active", "validFrom", "validUntil", "visitId"))
          && proof.path("active").isBoolean() && proof.path("visitId").isNull());
      BusinessId.parse(proof.path("assignmentId").asText());
      Instant from = Instant.parse(proof.path("validFrom").asText()), until = Instant.parse(proof.path("validUntil").asText());
      require(from.isBefore(until));
      Set<UUID> ids = new HashSet<>();
      if (operation.equals("APPOINTMENT_SEARCH")) {
        require(n.path("patientIds").isArray() && n.path("patientIds").size() <= 1000);
        for (var id : n.path("patientIds")) require(ids.add(BusinessId.parse(id.asText())));
      } else require(n.path("patientIds").isNull());
      boolean current = proof.path("active").asBoolean() && !now.isBefore(from) && now.isBefore(until);
      return new Decision(current, current ? Set.copyOf(ids) : Set.of());
    } catch (AppointmentError e) { if (e.status() == 400) throw AppointmentError.unavailable(); throw e; }
    catch (RuntimeException e) { throw AppointmentError.unavailable(); }
  }
  private static void require(boolean ok) { if (!ok) throw AppointmentError.unavailable(); }
}
