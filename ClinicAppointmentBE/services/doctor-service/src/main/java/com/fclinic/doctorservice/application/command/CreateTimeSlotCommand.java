package com.fclinic.doctorservice.application.command;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateTimeSlotCommand(
        Long doctorId,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime
) {}
