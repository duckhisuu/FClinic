package com.fclinic.appointmentservice.application.port.out;

import java.util.List;
import java.util.UUID;

public interface DoctorPatientAssignmentRepository {
    List<UUID> assignedPatients(UUID doctorId);
}
