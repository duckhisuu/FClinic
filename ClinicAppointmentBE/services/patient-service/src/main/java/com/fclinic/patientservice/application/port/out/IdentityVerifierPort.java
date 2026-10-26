package com.fclinic.patientservice.application.port.out;
import com.fclinic.patientservice.application.security.TrustedCaller;
public interface IdentityVerifierPort {
    /** Verifies opaque credentials, current activation and trusted mappings; throws typed 401/403/503. */
    TrustedCaller verify(String bearerToken);
}
