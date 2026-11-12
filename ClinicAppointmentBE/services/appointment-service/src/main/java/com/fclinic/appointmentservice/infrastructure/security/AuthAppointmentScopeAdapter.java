package com.fclinic.appointmentservice.infrastructure.security;

import static com.fclinic.appointmentservice.infrastructure.client.config.FeignHeaderGuards.require;
import static com.fclinic.appointmentservice.infrastructure.client.config.WireValues.id;
import com.fclinic.appointmentservice.application.AppointmentActor;
import com.fclinic.appointmentservice.application.port.out.AppointmentScopePort;
import com.fclinic.appointmentservice.config.AppointmentSecuritySettings;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.infrastructure.client.auth.AuthScopeClient;
import com.fclinic.appointmentservice.infrastructure.client.auth.dto.ScopeQueryDto;
import com.fclinic.appointmentservice.infrastructure.client.config.FeignCalls;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AuthAppointmentScopeAdapter implements AppointmentScopePort {
  private final AuthScopeClient client;
  private final AppointmentSecuritySettings settings;
  private final Clock clock;
  public AuthAppointmentScopeAdapter(AuthScopeClient client, AppointmentSecuritySettings settings, Clock clock) {
    this.client = client; this.settings = settings; this.clock = clock;
  }
  public boolean allowed(AppointmentActor actor, String operation, UUID patientId) { return evaluate(actor, operation, patientId).allowed(); }
  public Set<UUID> patients(AppointmentActor actor) {
    var decision = evaluate(actor, "APPOINTMENT_SEARCH", null);
    if (!decision.allowed()) throw new AppointmentError(403, "FORBIDDEN", "Chưa được cấp phạm vi tra cứu lịch hẹn.");
    return decision.patients();
  }
  private record Decision(boolean allowed, Set<UUID> patients) {}
  private static String nullable(UUID value) { return value == null ? null : value.toString(); }
  private Decision evaluate(AppointmentActor actor, String operation, UUID patientId) {
    Instant requested = clock.instant();
    var who = new ScopeQueryDto.Caller(actor.subject(), actor.principalType(), nullable(actor.patientId()), nullable(actor.doctorId()), null);
    var query = new ScopeQueryDto("appointment-work-scope/1", UUID.randomUUID().toString(), requested.toString(),
        "ADMINISTRATIVE", operation, who, nullable(patientId), null, null);
    return FeignCalls.call(() -> {
      var n = client.evaluate(settings.scopeUri(), query, FeignCalls.options(actor.deadlineNanos()));
      require(n != null && "appointment-work-scope/1".equals(n.contractVersion()) && query.equals(n.request()) && n.allowed() != null);
      Instant now = clock.instant(), evaluated = Instant.parse(n.evaluatedAt());
      require(!evaluated.isBefore(requested.minusSeconds(1)) && !evaluated.isAfter(now.plusSeconds(1)));
      if (!n.allowed()) {
        require(n.patientIds() == null && n.evidence() == null);
        return new Decision(false, Set.of());
      }
      var proof = n.evidence(); require(proof != null && proof.active() != null && proof.visitId() == null);
      id(proof.assignmentId());
      Instant from = Instant.parse(proof.validFrom()), until = Instant.parse(proof.validUntil()); require(from.isBefore(until));
      Set<UUID> ids = new HashSet<>();
      if (operation.equals("APPOINTMENT_SEARCH")) {
        require(n.patientIds() != null && n.patientIds().size() <= 1000);
        for (var value : n.patientIds()) require(ids.add(id(value)));
      } else require(n.patientIds() == null);
      boolean current = proof.active() && !now.isBefore(from) && now.isBefore(until);
      return new Decision(current, current ? Set.copyOf(ids) : Set.of());
    });
  }
}
