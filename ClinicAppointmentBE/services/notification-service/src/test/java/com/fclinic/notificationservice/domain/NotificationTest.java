package com.fclinic.notificationservice.domain;

import com.fclinic.notificationservice.domain.aggregate.Notification;
import com.fclinic.notificationservice.domain.model.NotificationStatus;
import com.fclinic.notificationservice.domain.model.NotificationType;
import com.fclinic.notificationservice.domain.model.RecipientType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");

    private Notification pending() {
        return Notification.pending(UUID.randomUUID(), 1L, 2L, RecipientType.PATIENT, "A", "a@x.com",
                NotificationType.APPOINTMENT_CONFIRMATION, "s", "<p>b</p>", NOW);
    }

    @Test
    void scheduleRetryIncrementsAttemptsAndSetsNextAttempt() {
        Notification n = pending();
        n.scheduleRetry(NOW.plusSeconds(60), "ERR", "boom", NOW);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.RETRY_SCHEDULED);
        assertThat(n.getAttemptCount()).isEqualTo(1);
        assertThat(n.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void permanentFailureIsManuallyRetryableAndResetGivesFreshBudget() {
        Notification n = pending();
        n.markPermanentFailure("ERR", "boom", NOW);
        assertThat(n.isManuallyRetryable()).isTrue();

        n.resetForManualRetry(NOW);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.RETRY_SCHEDULED);
        assertThat(n.getAttemptCount()).isZero();
        assertThat(n.getLastErrorCode()).isNull();
    }

    @Test
    void recoveryDoesNotCountAsFailedAttempt() {
        Notification n = pending();
        n.recoverFromStuckProcessing(NOW);

        assertThat(n.getAttemptCount()).isZero();
        assertThat(n.getLastErrorCode()).isEqualTo(Notification.RECOVERED_STUCK_CODE);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.RETRY_SCHEDULED);
    }

    @Test
    void markSentClearsErrors() {
        Notification n = pending();
        n.scheduleRetry(NOW, "ERR", "boom", NOW);
        n.markSent(NOW);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getLastErrorCode()).isNull();
        assertThat(n.getAttemptCount()).isEqualTo(1);
    }
}
