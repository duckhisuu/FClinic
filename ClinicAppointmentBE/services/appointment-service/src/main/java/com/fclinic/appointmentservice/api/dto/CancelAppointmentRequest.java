package com.fclinic.appointmentservice.api.dto;

import jakarta.validation.constraints.*;

public record CancelAppointmentRequest(
    @NotNull @PositiveOrZero @Max(9007199254740991L) Long version,
    @Size(max = 500) String reason) {}
