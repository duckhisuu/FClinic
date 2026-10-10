package com.fclinic.notificationservice.domain.aggregate;

import com.fclinic.notificationservice.domain.model.NotificationChannel;
import com.fclinic.notificationservice.domain.model.NotificationStatus;
import com.fclinic.notificationservice.domain.model.NotificationType;
import com.fclinic.notificationservice.domain.model.RecipientType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable email delivery job. Failed-attempt convention: attemptCount starts at 0 and is
 * incremented only when an SMTP attempt fails; success does not increment it.
 */
@Getter
@Builder(access = AccessLevel.PUBLIC, toBuilder = true)
public class Notification {

    public static final String RECOVERED_STUCK_CODE = "RECOVERED_STUCK_PROCESSING";

    private final Long id;
    private final UUID eventId;
    private final Long appointmentId;
    private final Long recipientUserId;
    private final RecipientType recipientType;
    private final String recipientName;
    private final String recipientEmail;
    private final NotificationType type;
    private final NotificationChannel channel;
    private final String subject;
    private final String htmlBody;
    private NotificationStatus status;
    private int attemptCount;
    private Instant nextAttemptAt;
    private Instant lastAttemptAt;
    private Instant sentAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;

    public static Notification pending(UUID eventId, Long appointmentId, Long recipientUserId,
                                       RecipientType recipientType, String recipientName, String recipientEmail,
                                       NotificationType type, String subject, String htmlBody, Instant now) {
        return Notification.builder()
                .eventId(eventId)
                .appointmentId(appointmentId)
                .recipientUserId(recipientUserId)
                .recipientType(recipientType)
                .recipientName(recipientName)
                .recipientEmail(recipientEmail)
                .type(type)
                .channel(NotificationChannel.EMAIL)
                .subject(subject)
                .htmlBody(htmlBody)
                .status(NotificationStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void markSent(Instant now) {
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
        this.nextAttemptAt = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = now;
    }

    public void scheduleRetry(Instant nextAttemptAt, String errorCode, String errorMessage, Instant now) {
        this.attemptCount++;
        this.status = NotificationStatus.RETRY_SCHEDULED;
        this.nextAttemptAt = nextAttemptAt;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = truncate(errorMessage, 1000);
        this.updatedAt = now;
    }

    public void markPermanentFailure(String errorCode, String errorMessage, Instant now) {
        this.attemptCount++;
        this.status = NotificationStatus.PERMANENT_FAILURE;
        this.nextAttemptAt = null;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = truncate(errorMessage, 1000);
        this.updatedAt = now;
    }

    /** Crashed worker: SMTP outcome unknown, so the failed-attempt counter is left untouched. */
    public void recoverFromStuckProcessing(Instant now) {
        this.status = NotificationStatus.RETRY_SCHEDULED;
        this.nextAttemptAt = now;
        this.lastErrorCode = RECOVERED_STUCK_CODE;
        this.updatedAt = now;
    }

    public boolean isManuallyRetryable() {
        return status == NotificationStatus.PERMANENT_FAILURE;
    }

    /** Gives the notification a fresh automatic retry budget. */
    public void resetForManualRetry(Instant now) {
        this.status = NotificationStatus.RETRY_SCHEDULED;
        this.attemptCount = 0;
        this.nextAttemptAt = now;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = now;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
