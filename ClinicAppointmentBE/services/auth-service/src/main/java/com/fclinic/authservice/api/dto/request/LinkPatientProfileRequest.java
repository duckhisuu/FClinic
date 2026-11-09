package com.fclinic.authservice.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record LinkPatientProfileRequest(
        @NotNull(message = "Mã hồ sơ bệnh nhân không được để trống")
        UUID patientId
) {
}
