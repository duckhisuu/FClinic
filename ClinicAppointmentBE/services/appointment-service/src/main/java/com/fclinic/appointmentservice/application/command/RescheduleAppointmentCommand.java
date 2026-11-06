package com.fclinic.appointmentservice.application.command;

import com.fclinic.appointmentservice.application.AppointmentActor;
import java.time.*;
import java.util.*;

public record RescheduleAppointmentCommand(
    AppointmentActor actor,
    UUID idempotencyKey,
    UUID appointmentId,
    long version,
    UUID slotId,
    String reason) {}
