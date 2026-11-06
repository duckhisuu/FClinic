package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record SlotView(
    UUID id,
    UUID doctorId,
    LocalDate slotDate,
    LocalTime startTime,
    LocalTime endTime,
    boolean available) {}
