package com.fclinic.appointmentservice.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.*;

public record RescheduleAppointmentRequest(
    @NotNull @PositiveOrZero @Max(9007199254740991L) Long version,
    @NotNull UUID slotId,
    @Size(max = 500) String reason) {}
