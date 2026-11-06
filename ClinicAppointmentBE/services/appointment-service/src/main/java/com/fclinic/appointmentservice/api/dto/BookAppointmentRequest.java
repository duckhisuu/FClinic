package com.fclinic.appointmentservice.api.dto;

import java.util.UUID;
import com.fclinic.appointmentservice.domain.vo.BusinessId;

import jakarta.validation.constraints.*;

public record BookAppointmentRequest(
    @NotBlank @Pattern(regexp = BusinessId.PATTERN)
        String patientId,
    @NotNull UUID doctorId,
    @NotNull UUID slotId,
    String reasonForVisit) {}
