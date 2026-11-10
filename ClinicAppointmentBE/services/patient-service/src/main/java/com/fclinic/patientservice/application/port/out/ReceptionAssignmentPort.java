package com.fclinic.patientservice.application.port.out;
import com.fclinic.patientservice.application.security.TrustedCaller;
import java.util.Set;
import java.util.UUID;

public interface ReceptionAssignmentPort {
    boolean enabled();
    void assign(TrustedCaller caller, Set<UUID> patientIds, String action);
}
