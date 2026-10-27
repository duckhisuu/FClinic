package com.fclinic.appointmentservice.api.dto;

import jakarta.validation.constraints.*;

public record BookAppointmentRequest(
    @NotBlank @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        String patientId,
    @NotNull @Positive @Max(9007199254740991L) Long doctorId,
    @NotNull @Positive @Max(9007199254740991L) Long slotId,
    String reasonForVisit) {}
