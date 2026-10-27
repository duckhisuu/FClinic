package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record ReminderReferenceView(
    long id,
    UUID patientId,
    long doctorId,
    String status,
    long version,
    long slotId,
    LocalDate appointmentDate,
    LocalTime startTime,
    LocalTime endTime,
    String timezone) {}
