package com.fclinic.authservice.infrastructure.adapter;

import com.fclinic.authservice.application.port.out.ScopeAssignmentPort;
import com.fclinic.authservice.infrastructure.config.ScopeAuthorityProperties;
import java.util.*;
import org.springframework.stereotype.Component;

/** Explicit assignments in protected deployment configuration; edits require reload/restart. */
@Component
public class ConfiguredScopeAssignments implements ScopeAssignmentPort {
  private final Map<String, ServiceIdentity> services;
  private final Map<UUID, Grant> grants;
  public ConfiguredScopeAssignments(ScopeAuthorityProperties settings) {
    Map<String, ServiceIdentity> identities = new LinkedHashMap<>();
    settings.services().forEach((key, value) -> identities.put(key,
        new ServiceIdentity(value.subject(), value.name(), value.authorities())));
    Map<UUID, Grant> assignments = new LinkedHashMap<>();
    settings.grants().forEach((key, value) -> assignments.put(key,
        new Grant(value.subject(), value.operations(), value.patientIds(), value.allPatients(), value.active(),
            value.validFrom(), value.validUntil(), value.appointmentId(), value.visitDate(), value.visitId())));
    services = Collections.unmodifiableMap(identities); grants = Collections.unmodifiableMap(assignments);
  }
  public Map<String, ServiceIdentity> services() { return services; }
  public Map<UUID, Grant> grants() { return grants; }
}
