package com.fclinic.notificationservice.api.dto;

import com.fclinic.notificationservice.application.result.NotificationView;
import com.fclinic.notificationservice.domain.model.NotificationChannel;
import com.fclinic.notificationservice.domain.model.NotificationStatus;
import com.fclinic.notificationservice.domain.model.NotificationType;
import com.fclinic.notificationservice.domain.model.RecipientType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        Long id, UUID eventId, Long appointmentId, Long recipientUserId, RecipientType recipientType,
        String recipientName, String recipientEmail, NotificationType type, NotificationChannel channel,
        NotificationStatus status, String subject, int attemptCount, Instant nextAttemptAt, Instant lastAttemptAt,
        Instant sentAt, String lastErrorCode, String lastErrorMessage, Instant createdAt, Instant updatedAt
) {
    public static NotificationResponse from(NotificationView v) {
        return new NotificationResponse(v.id(), v.eventId(), v.appointmentId(), v.recipientUserId(),
                v.recipientType(), v.recipientName(), v.recipientEmail(), v.type(), v.channel(), v.status(),
                v.subject(), v.attemptCount(), v.nextAttemptAt(), v.lastAttemptAt(), v.sentAt(),
                v.lastErrorCode(), v.lastErrorMessage(), v.createdAt(), v.updatedAt());
    }
}
