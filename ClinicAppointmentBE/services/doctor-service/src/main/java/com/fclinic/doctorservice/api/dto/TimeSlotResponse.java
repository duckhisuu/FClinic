package com.fclinic.doctorservice.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record TimeSlotResponse(
        Long id,
        Long doctorId,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        boolean isBooked
) {
}
