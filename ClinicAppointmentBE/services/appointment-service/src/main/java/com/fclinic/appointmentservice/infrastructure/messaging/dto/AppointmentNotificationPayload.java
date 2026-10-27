package com.fclinic.appointmentservice.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Integration event payload schema published to RabbitMQ exchange 'clinic.events'.
 * Matches the inbound contract consumed by Notification Service.
 */
public record AppointmentNotificationPayload(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String source,
        AppointmentSnapshotPayload appointment,
        RecipientPayload patient,
        RecipientPayload doctor,
        PreviousSlotPayload previousSlot
) {
    public record AppointmentSnapshotPayload(
            Long appointmentId,
            @JsonFormat(pattern = "yyyy-MM-dd")
            LocalDate appointmentDate,
            @JsonFormat(pattern = "HH:mm:ss")
            LocalTime startTime,
            @JsonFormat(pattern = "HH:mm:ss")
            LocalTime endTime,
            String clinicName,
            String roomName,
            String reason,
            String cancellationReason
    ) {}

    public record RecipientPayload(
            Long userId,
            String recipientType,
            String displayName,
            String email
    ) {}

    public record PreviousSlotPayload(
            @JsonFormat(pattern = "yyyy-MM-dd")
            LocalDate appointmentDate,
            @JsonFormat(pattern = "HH:mm:ss")
            LocalTime startTime,
            @JsonFormat(pattern = "HH:mm:ss")
            LocalTime endTime
    ) {}
}
