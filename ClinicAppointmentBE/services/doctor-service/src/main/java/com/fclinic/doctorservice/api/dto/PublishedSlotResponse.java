package com.fclinic.doctorservice.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record PublishedSlotResponse(
        UUID id,
        UUID doctorId,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        boolean published
) {
}
