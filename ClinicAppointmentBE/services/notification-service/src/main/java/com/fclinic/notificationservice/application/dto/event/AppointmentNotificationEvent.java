package com.fclinic.notificationservice.application.dto.event;

import com.fclinic.notificationservice.application.dto.PreviousAppointmentSlotDto;
import com.fclinic.notificationservice.application.dto.RecipientDto;
import com.fclinic.notificationservice.domain.model.AppointmentEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** Inbound contract published by the appointment lifecycle producer. previousSlot is required for reschedules. */
public record AppointmentNotificationEvent(
        @NotNull UUID eventId,
        @NotNull AppointmentEventType eventType,
        @NotNull Instant occurredAt,
        @NotBlank String source,
        @Valid @NotNull AppointmentSnapshotDto appointment,
        @Valid @NotNull RecipientDto patient,
        @Valid @NotNull RecipientDto doctor,
        @Valid PreviousAppointmentSlotDto previousSlot
) {
}
