package com.fclinic.appointmentservice.api.dto;

import jakarta.validation.constraints.*;

public record RescheduleAppointmentRequest(
    @NotNull @PositiveOrZero @Max(9007199254740991L) Long version,
    @NotNull @Positive @Max(9007199254740991L) Long slotId,
    @Size(max = 500) String reason) {}
