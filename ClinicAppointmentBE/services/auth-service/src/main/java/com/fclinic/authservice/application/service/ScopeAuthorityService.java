package com.fclinic.authservice.application.service;

import com.fclinic.authservice.application.port.out.UserRepositoryPort;
import com.fclinic.authservice.domain.util.UuidV4;
import com.fclinic.authservice.domain.valueobject.RoleName;
import com.fclinic.authservice.application.port.out.ScopeAssignmentPort;
import com.fclinic.authservice.application.port.out.DoctorPatientAssignmentPort;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScopeAuthorityService {
  private final UserRepositoryPort users;
  private final ScopeAssignmentPort settings;
  private final Clock clock;
  private final DoctorPatientAssignmentPort appointments;
  public ScopeAuthorityService(UserRepositoryPort users, ScopeAssignmentPort settings, Clock clock, DoctorPatientAssignmentPort appointments) {
    this.users = users; this.settings = settings; this.clock = clock; this.appointments = appointments;
  }
  public record Caller(String subject, String principalType, UUID patientId, UUID doctorId, String serviceName) { }
  public record Query(String contractVersion, UUID nonce, Instant requestedAt, String authority,
      String operation, Caller caller, UUID patientId, UUID appointmentId, Instant visitDate) { }

  @Transactional(readOnly = true)
  public Map<String, Object> evaluate(Query q) {
    Instant now = clock.instant();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("contractVersion", q.contractVersion()); result.put("request", q);
    result.put("evaluatedAt", now); result.put("allowed", false); result.put("patientIds", null); result.put("evidence", null);
    if (!UuidV4.isUuidV4(q.nonce()) || (q.patientId() != null && !UuidV4.isUuidV4(q.patientId()))
        || (q.appointmentId() != null && !UuidV4.isUuidV4(q.appointmentId())) || q.requestedAt() == null || q.requestedAt().isBefore(now.minusSeconds(5))
        || q.requestedAt().isAfter(now.plusSeconds(1)) || !permittedIdentity(q)) return result;
    // Read access follows the selected doctor in committed bookings. Cancellation revokes it
    // when no other confirmed/completed appointment connects this doctor and patient.
    if ("CLINICAL".equals(q.authority()) && Set.of("DOCTOR_SEARCH", "DOCTOR_PROFILE_READ", "MEDICAL_READ").contains(q.operation())) {
      Set<UUID> patients = appointments.assignedPatients(q.caller().doctorId());
      boolean search = "DOCTOR_SEARCH".equals(q.operation());
      if (patients.size() > 1000 || (!search && (q.patientId() == null || !patients.contains(q.patientId())))) return result;
      Map<String, Object> evidence = new LinkedHashMap<>();
      evidence.put("assignmentId", q.caller().doctorId()); evidence.put("active", true);
      evidence.put("validFrom", now.minusSeconds(1)); evidence.put("validUntil", now.plusSeconds(30)); evidence.put("visitId", null);
      result.put("allowed", true); result.put("patientIds", search ? patients : null); result.put("evidence", evidence);
      return result;
    }
    boolean search = Set.of("APPOINTMENT_SEARCH", "ADMIN_SEARCH", "DOCTOR_SEARCH").contains(q.operation());
    for (var entry : settings.grants().entrySet()) {
      var grant = entry.getValue();
      if (!UuidV4.isUuidV4(entry.getKey()) || !grant.active() || grant.validFrom() == null || grant.validUntil() == null
          || !grant.validFrom().isBefore(grant.validUntil()) || now.isBefore(grant.validFrom()) || !now.isBefore(grant.validUntil())
          || !q.caller().subject().equals(grant.subject()) || !grant.operations().contains(q.operation())) continue;
      boolean machine = "SERVICE".equals(q.caller().principalType());
      // Human assignments are always finite patient sets; no role-wide or wildcard clinical grant.
      if (grant.allPatients() && !machine) continue;
      if (q.patientId() != null && !(machine && grant.allPatients()) && !grant.patientIds().contains(q.patientId())) continue;
      if (q.patientId() == null && !search && !q.operation().startsWith("ADMIN_ASSIGNMENT_")
          && !q.operation().equals("APPOINTMENT_AVAILABILITY")) continue;
      if (q.operation().equals("VALID_VISIT") && (grant.visitDate() == null || !UuidV4.isUuidV4(grant.visitId())
          || !Objects.equals(grant.appointmentId(), q.appointmentId()) || !grant.visitDate().equals(q.visitDate()))) continue;
      if (grant.patientIds().size() > 1000) continue;
      Map<String, Object> evidence = new LinkedHashMap<>();
      evidence.put("assignmentId", entry.getKey()); evidence.put("active", true);
      evidence.put("validFrom", grant.validFrom()); evidence.put("validUntil", grant.validUntil());
      evidence.put("visitId", q.operation().equals("VALID_VISIT") ? grant.visitId() : null);
      result.put("allowed", true); result.put("patientIds", search ? grant.patientIds() : null); result.put("evidence", evidence);
      return result;
    }
    return result;
  }
  private boolean permittedIdentity(Query q) {
    if (q.caller() == null || q.caller().subject() == null || q.operation() == null) return false;
    var caller = q.caller();
    if ("SERVICE".equals(caller.principalType())) {
      var binding = settings.services().values().stream().filter(b -> caller.subject().equals(b.subject())).findFirst().orElse(null);
      return "patient-work-scope/2".equals(q.contractVersion()) && "ADMINISTRATIVE".equals(q.authority())
          && "APPOINTMENT_PROFILE_READ".equals(q.operation()) && caller.patientId() == null && caller.doctorId() == null
          && binding != null && "appointment-service".equals(binding.name()) && binding.name().equals(caller.serviceName())
          && binding.authorities() != null && binding.authorities().contains("SVC_PATIENT_READ");
    }
    if (!"USER".equals(caller.principalType()) || caller.serviceName() != null || !UuidV4.isUuidV4String(caller.subject())) return false;
    var found = users.findByKeycloakId(UUID.fromString(caller.subject()));
    if (found.isEmpty() || !found.get().isActive()) return false;
    var user = found.get();
    if (!Objects.equals(user.getPatientId(), caller.patientId()) || !Objects.equals(user.getDoctorId(), caller.doctorId())) return false;
    if ("appointment-work-scope/1".equals(q.contractVersion()))
      return "ADMINISTRATIVE".equals(q.authority()) && user.getRoleName() == RoleName.ROLE_STAFF
          && Set.of("APPOINTMENT_CREATE", "APPOINTMENT_READ", "APPOINTMENT_CANCEL", "APPOINTMENT_RESCHEDULE",
              "APPOINTMENT_SEARCH", "APPOINTMENT_AVAILABILITY").contains(q.operation());
    if (!"patient-work-scope/2".equals(q.contractVersion())) return false;
    if ("ADMINISTRATIVE".equals(q.authority()))
      return user.getRoleName() == RoleName.ROLE_STAFF && Set.of("ADMIN_ASSIGNMENT_CREATE", "ADMIN_ASSIGNMENT_READ",
          "ADMIN_ASSIGNMENT_UPDATE", "ADMIN_ASSIGNMENT_SEARCH", "ADMIN_PROFILE_READ", "ADMIN_PROFILE_UPDATE", "ADMIN_SEARCH").contains(q.operation());
    return "CLINICAL".equals(q.authority()) && user.getRoleName() == RoleName.ROLE_DOCTOR && user.getDoctorId() != null
        && Set.of("DOCTOR_PROFILE_READ", "DOCTOR_SEARCH", "MEDICAL_READ", "MEDICAL_CREATE", "MEDICAL_UPDATE", "VALID_VISIT").contains(q.operation());
  }
}
