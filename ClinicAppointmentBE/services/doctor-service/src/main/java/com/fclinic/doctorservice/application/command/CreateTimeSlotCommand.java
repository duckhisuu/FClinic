package com.fclinic.doctorservice.application.command;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateTimeSlotCommand(
        UUID doctorId,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime
) {}
