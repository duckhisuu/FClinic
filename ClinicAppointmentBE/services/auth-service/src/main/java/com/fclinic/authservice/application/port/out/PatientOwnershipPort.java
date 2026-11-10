package com.fclinic.authservice.application.port.out;

import java.util.UUID;

public interface PatientOwnershipPort {
    /** Patient Service derives ownership from the delegated, independently verified bearer. */
    UUID selfRegisteredPatient(String authorization);
}
