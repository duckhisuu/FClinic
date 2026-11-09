package com.fclinic.authservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Instant;
import java.util.*;

/** Deployment-owned assignments. Empty grants deny; identities never infer work scope from role alone. */
@ConfigurationProperties("app.authorization")
public record ScopeAuthorityProperties(Map<String, Client> clients, Map<String, Service> services,
    Map<UUID, Grant> grants) {
  public ScopeAuthorityProperties {
    clients = clients == null ? Map.of() : Map.copyOf(clients);
    services = services == null ? Map.of() : Map.copyOf(services);
    grants = grants == null ? Map.of() : Map.copyOf(grants);
  }
  public record Client(String clientId, String secret, String contractVersion, String authority) {
    @Override public String toString() { return "ScopeClient[redacted]"; }
  }
  public record Service(String subject, String name, Set<String> authorities) { }
  public record Grant(String subject, Set<String> operations, Set<UUID> patientIds,
      @DefaultValue("false") boolean allPatients, @DefaultValue("true") boolean active,
      @DefaultValue("1970-01-01T00:00:00Z") Instant validFrom,
      @DefaultValue("9999-12-31T23:59:59Z") Instant validUntil,
      UUID appointmentId, Instant visitDate, UUID visitId) {
    public Grant {
      operations = operations == null ? Set.of() : Set.copyOf(operations);
      patientIds = patientIds == null ? Set.of() : Set.copyOf(patientIds);
    }
  }
  @Override public String toString() { return "ScopeAuthorityProperties[redacted]"; }
}
