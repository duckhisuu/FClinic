package com.fclinic.patientservice.application.port.out;
import java.util.Optional;
public interface DoctorClientPort {
    record DoctorReference(long id, boolean active) { }
    Optional<DoctorReference> find(long doctorId);
}
