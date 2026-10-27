package com.fclinic.appointmentservice.application.command;

import com.fclinic.appointmentservice.application.AppointmentActor;
import java.time.*;
import java.util.*;

public record RescheduleAppointmentCommand(
    AppointmentActor actor,
    UUID idempotencyKey,
    long appointmentId,
    long version,
    long slotId,
    String reason) {}
