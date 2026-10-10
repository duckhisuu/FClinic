package com.fclinic.notificationservice.application.result;

import com.fclinic.notificationservice.domain.aggregate.Notification;
import com.fclinic.notificationservice.domain.model.NotificationChannel;
import com.fclinic.notificationservice.domain.model.NotificationStatus;
import com.fclinic.notificationservice.domain.model.NotificationType;
import com.fclinic.notificationservice.domain.model.RecipientType;

import java.time.Instant;
import java.util.UUID;

public record NotificationView(
        Long id,
        UUID eventId,
        Long appointmentId,
        Long recipientUserId,
        RecipientType recipientType,
        String recipientName,
        String recipientEmail,
        NotificationType type,
        NotificationChannel channel,
        NotificationStatus status,
        String subject,
        int attemptCount,
        Instant nextAttemptAt,
        Instant lastAttemptAt,
        Instant sentAt,
        String lastErrorCode,
        String lastErrorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static NotificationView from(Notification n) {
        return new NotificationView(n.getId(), n.getEventId(), n.getAppointmentId(), n.getRecipientUserId(),
                n.getRecipientType(), n.getRecipientName(), n.getRecipientEmail(), n.getType(), n.getChannel(),
                n.getStatus(), n.getSubject(), n.getAttemptCount(), n.getNextAttemptAt(), n.getLastAttemptAt(),
                n.getSentAt(), n.getLastErrorCode(), n.getLastErrorMessage(), n.getCreatedAt(), n.getUpdatedAt());
    }
}
