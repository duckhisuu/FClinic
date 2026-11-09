package com.fclinic.authservice.application.port.out;

import java.time.Instant;
import java.util.*;

public interface ScopeAssignmentPort {
  record ServiceIdentity(String subject, String name, Set<String> authorities) { }
  record Grant(String subject, Set<String> operations, Set<UUID> patientIds, boolean allPatients,
      boolean active, Instant validFrom, Instant validUntil, UUID appointmentId, Instant visitDate, UUID visitId) { }
  Map<String, ServiceIdentity> services();
  Map<UUID, Grant> grants();
}
