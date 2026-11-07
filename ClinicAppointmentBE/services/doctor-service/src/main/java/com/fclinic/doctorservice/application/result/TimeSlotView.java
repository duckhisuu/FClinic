package com.fclinic.doctorservice.application.result;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record TimeSlotView(
        UUID id,
        UUID doctorId,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        boolean isBooked,
        boolean published
) {
}
