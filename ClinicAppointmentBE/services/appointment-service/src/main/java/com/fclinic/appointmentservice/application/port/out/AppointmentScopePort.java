package com.fclinic.appointmentservice.application.port.out;

import com.fclinic.appointmentservice.application.AppointmentActor;
import java.util.Set;
import java.util.UUID;

public interface AppointmentScopePort {
  boolean allowed(AppointmentActor actor, String operation, UUID patientId);
  Set<UUID> patients(AppointmentActor actor);
}
