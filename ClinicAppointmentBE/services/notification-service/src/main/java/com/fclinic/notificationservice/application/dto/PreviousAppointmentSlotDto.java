package com.fclinic.notificationservice.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record PreviousAppointmentSlotDto(
        @NotNull LocalDate appointmentDate,
        @NotNull LocalTime startTime
) {
}
