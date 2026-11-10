package com.fclinic.authservice.application.port.out;

import java.util.Set;
import java.util.UUID;

/** Current care relationships from committed appointments, never browser claims or seed UUIDs. */
public interface DoctorPatientAssignmentPort {
    Set<UUID> assignedPatients(UUID doctorId);
}
