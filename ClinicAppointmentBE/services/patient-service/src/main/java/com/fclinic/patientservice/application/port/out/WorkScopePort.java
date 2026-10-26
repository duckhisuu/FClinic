package com.fclinic.patientservice.application.port.out;
import com.fclinic.patientservice.application.security.TrustedCaller;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface WorkScopePort {
    enum AdministrativeAction { CREATE, READ, UPDATE, SEARCH }
    enum ProfileAction { ADMIN_READ, ADMIN_UPDATE, DOCTOR_READ }
    enum ClinicalAction { READ, CREATE, UPDATE }
    enum SearchAction { ADMINISTRATIVE, CLINICAL }
    record SearchScope(Set<UUID> patientIds) {
        public SearchScope { patientIds = Set.copyOf(patientIds); }
    }
    boolean receptionist(TrustedCaller caller);
    boolean profile(TrustedCaller caller, UUID patientId);
    boolean clinical(TrustedCaller caller, UUID patientId);
    boolean validVisit(TrustedCaller caller, UUID patientId, Long appointmentId, Instant visitDate);
    boolean appointmentProfileLookup(TrustedCaller caller, UUID patientId);
    SearchScope search(TrustedCaller caller);
    // Compatibility defaults preserve existing port implementations; production overrides exact operations.
    default boolean receptionist(TrustedCaller c, AdministrativeAction action) { return receptionist(c); }
    default boolean profile(TrustedCaller c, UUID id, ProfileAction action) { return profile(c,id); }
    default boolean clinical(TrustedCaller c, UUID id, ClinicalAction action) { return clinical(c,id); }
    default SearchScope search(TrustedCaller c, SearchAction action) { return search(c); }
}
