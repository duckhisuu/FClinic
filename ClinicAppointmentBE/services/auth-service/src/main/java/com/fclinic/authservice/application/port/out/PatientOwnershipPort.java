package com.fclinic.authservice.application.port.out;

import java.util.UUID;

public interface PatientOwnershipPort {
    boolean owns(UUID subject, UUID patientId, String authorization);
}
