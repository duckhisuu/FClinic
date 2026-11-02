package com.fclinic.patientservice.application.port.out;
import java.util.Optional;
import java.util.UUID;
public interface AppointmentClientPort {
    /** Internal facts only; actual SERVICE wire projection is still D-19. */
    record AppointmentReference(UUID id, UUID patientId, UUID doctorId, boolean cancelled) { }
    Optional<AppointmentReference> find(UUID appointmentId);
}
