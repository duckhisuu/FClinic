package com.fclinic.patientservice.application.port.out;
import java.util.UUID;
import java.util.Optional;
public interface DoctorClientPort {
    record DoctorReference(UUID id, boolean active) { }
    Optional<DoctorReference> find(UUID doctorId);
}
