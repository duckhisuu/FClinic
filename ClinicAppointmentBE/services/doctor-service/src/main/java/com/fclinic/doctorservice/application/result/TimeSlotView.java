package com.fclinic.doctorservice.application.result;

import java.time.LocalDate;
import java.time.LocalTime;

public record TimeSlotView(
        Long id,
        Long doctorId,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        boolean isBooked
) {
}
