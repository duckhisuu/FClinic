package com.fclinic.appointmentservice.application.command;

import com.fclinic.appointmentservice.application.AppointmentActor;
import java.time.*;
import java.util.*;

public record CancelAppointmentCommand(
    AppointmentActor actor, UUID idempotencyKey, UUID appointmentId, long version, String reason) {}
