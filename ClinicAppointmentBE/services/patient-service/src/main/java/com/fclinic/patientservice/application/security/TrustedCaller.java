package com.fclinic.patientservice.application.security;
import java.util.Set;
import java.util.UUID;

/** Internal verified context, not a declaration of JWT claims or onboarding storage. */
public record TrustedCaller(String subject, PrincipalType principalType, Set<String> authorities,
                            UUID patientId, Long doctorId, String serviceName, boolean active) {
    public enum PrincipalType { USER, SERVICE }
    public TrustedCaller { authorities = authorities == null ? Set.of() : Set.copyOf(authorities); }
    public boolean userRole(String authority) {
        return principalType == PrincipalType.USER && authorities.contains(authority);
    }
}
