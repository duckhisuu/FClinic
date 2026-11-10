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
  private ReceptionGrantStore reception;
  private java.time.Clock clock = java.time.Clock.systemUTC();
  @org.springframework.beans.factory.annotation.Autowired
  public ConfiguredScopeAssignments(ScopeAuthorityProperties settings, ReceptionGrantStore reception, java.time.Clock clock) {
    this(settings); this.reception=reception; this.clock=clock;
  }
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
  public Map<UUID, Grant> grants() {
    if(reception==null) return grants;
    Map<UUID,Grant> result=new LinkedHashMap<>(grants);
    var now=clock.instant();
    for(var row:reception.load()) {
      var source=grants.get(row.source()); var g=row.grant();
      if(source==null || row.source().version()!=4 || row.source().variant()!=2 || !source.active() || source.allPatients() || !source.subject().equals(g.subject())
          || source.validFrom()==null || source.validUntil()==null || !source.validFrom().isBefore(source.validUntil())
          || now.isBefore(source.validFrom()) || !now.isBefore(source.validUntil())
          || now.isBefore(g.validFrom()) || !now.isBefore(g.validUntil())) continue;
      Set<String> ops=new HashSet<>(g.operations()); ops.retainAll(ReceptionGrantStore.administrativeOperations(source));
      if(ops.isEmpty()) continue;
      var until=g.validUntil().isBefore(source.validUntil())?g.validUntil():source.validUntil();
      var from=g.validFrom().isAfter(source.validFrom())?g.validFrom():source.validFrom();
      result.putIfAbsent(row.id(),new Grant(g.subject(),Set.copyOf(ops),g.patientIds(),false,true,from,until,null,null,null));
    }
    return Collections.unmodifiableMap(result);
  }
}
