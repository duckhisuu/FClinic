package com.fclinic.notificationservice.application.dto;

import com.fclinic.notificationservice.domain.model.RecipientType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RecipientDto(
        UUID userId,
        @NotNull RecipientType recipientType,
        @NotBlank String displayName,
        @Email String email
) {
}
