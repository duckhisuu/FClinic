package com.fclinic.appointmentservice.application.command;

import com.fclinic.appointmentservice.application.AppointmentActor;
import java.time.*;
import java.util.*;

public record BookAppointmentCommand(
    AppointmentActor actor,
    UUID idempotencyKey,
    UUID patientId,
    UUID doctorId,
    UUID slotId,
    String reasonForVisit) {}
