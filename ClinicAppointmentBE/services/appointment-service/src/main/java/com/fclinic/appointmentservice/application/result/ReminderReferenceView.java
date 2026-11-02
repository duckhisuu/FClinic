package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record ReminderReferenceView(
    UUID id,
    UUID patientId,
    UUID doctorId,
    String status,
    long version,
    UUID slotId,
    LocalDate appointmentDate,
    LocalTime startTime,
    LocalTime endTime,
    String timezone) {}
