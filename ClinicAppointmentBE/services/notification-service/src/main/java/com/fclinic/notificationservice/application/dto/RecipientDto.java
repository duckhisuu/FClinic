package com.fclinic.notificationservice.application.dto;

import com.fclinic.notificationservice.domain.model.RecipientType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecipientDto(
        @NotNull Long userId,
        @NotNull RecipientType recipientType,
        @NotBlank String displayName,
        @NotBlank @Email String email
) {
}
