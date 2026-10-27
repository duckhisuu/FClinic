package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record AppointmentHistoryView(
    long id,
    String action,
    long version,
    LocalDate oldDate,
    LocalTime oldStartTime,
    LocalDate newDate,
    LocalTime newStartTime,
    String reason,
    String actorRole,
    Instant createdAt) {}
