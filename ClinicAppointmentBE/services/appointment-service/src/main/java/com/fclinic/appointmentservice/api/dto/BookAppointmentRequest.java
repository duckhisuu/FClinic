package com.fclinic.appointmentservice.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.*;

public record BookAppointmentRequest(
    @NotBlank @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        String patientId,
    @NotNull UUID doctorId,
    @NotNull UUID slotId,
    String reasonForVisit) {}
