package com.fclinic.notificationservice.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentSnapshotDto(
        @NotNull UUID appointmentId,
        @NotNull LocalDate appointmentDate,
        @NotNull LocalTime startTime,
        LocalTime endTime,
        @NotBlank String clinicName,
        String roomName,
        String reason,
        String cancellationReason
) {
}
