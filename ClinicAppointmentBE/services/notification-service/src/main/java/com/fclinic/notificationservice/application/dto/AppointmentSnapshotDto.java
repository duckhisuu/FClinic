package com.fclinic.notificationservice.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentSnapshotDto(
        @NotNull Long appointmentId,
        @NotNull LocalDate appointmentDate,
        @NotNull LocalTime startTime,
        LocalTime endTime,
        @NotBlank String clinicName,
        String roomName,
        String reason,
        String cancellationReason
) {
}
