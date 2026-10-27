package com.fclinic.notificationservice.application;

import com.fclinic.notificationservice.application.exception.NotificationNotRetryableException;
import com.fclinic.notificationservice.application.port.out.NotificationRepositoryPort;
import com.fclinic.notificationservice.application.usecase.NotificationStateService;
import com.fclinic.notificationservice.config.NotificationProperties;
import com.fclinic.notificationservice.domain.aggregate.Notification;
import com.fclinic.notificationservice.domain.model.NotificationStatus;
import com.fclinic.notificationservice.domain.model.NotificationType;
import com.fclinic.notificationservice.domain.model.RecipientType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");

    private NotificationRepositoryPort repository;
    private NotificationStateService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepositoryPort.class);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new NotificationStateService(repository, new NotificationProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
    }

    private Notification notification(int failedAttempts) {
        Notification n = Notification.builder().id(1L).eventId(UUID.randomUUID()).appointmentId(5L)
                .recipientUserId(2L).recipientType(RecipientType.PATIENT).recipientName("A")
                .recipientEmail("a@x.com").type(NotificationType.APPOINTMENT_CONFIRMATION)
                .status(NotificationStatus.PROCESSING).attemptCount(failedAttempts).build();
        when(repository.findById(1L)).thenReturn(Optional.of(n));
        return n;
    }

    @Test
    void firstFailureSchedulesRetryAfterOneMinute() {
        Notification n = notification(0);
        service.recordFailure(1L, "SMTP_CONNECTION_ERROR", "down");

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.RETRY_SCHEDULED);
        assertThat(n.getAttemptCount()).isEqualTo(1);
        assertThat(n.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
    }

    @Test
    void secondFailureSchedulesRetryAfterFiveMinutes() {
        Notification n = notification(1);
        service.recordFailure(1L, "E", "down");

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.RETRY_SCHEDULED);
        assertThat(n.getAttemptCount()).isEqualTo(2);
        assertThat(n.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void thirdFailureIsPermanent() {
        Notification n = notification(2);
        service.recordFailure(1L, "E", "down");

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PERMANENT_FAILURE);
        assertThat(n.getAttemptCount()).isEqualTo(3);
        assertThat(n.getNextAttemptAt()).isNull();
    }

    @Test
    void manualRetryOnlyAllowedAfterPermanentFailure() {
        Notification n = notification(0);
        assertThatThrownBy(() -> service.manualRetry(1L)).isInstanceOf(NotificationNotRetryableException.class);

        n.markPermanentFailure("E", "m", NOW);
        Notification retried = service.manualRetry(1L);

        assertThat(retried.getStatus()).isEqualTo(NotificationStatus.RETRY_SCHEDULED);
        assertThat(retried.getAttemptCount()).isZero();
    }

    @Test
    void recoverStuckResetsWithoutCountingAttempt() {
        Notification n = notification(1);
        when(repository.findStuckProcessing(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(n));

        int recovered = service.recoverStuck();

        assertThat(recovered).isEqualTo(1);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.RETRY_SCHEDULED);
        assertThat(n.getAttemptCount()).isEqualTo(1);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findStuckProcessing(cutoff.capture(), org.mockito.ArgumentMatchers.anyInt());
        assertThat(cutoff.getValue()).isEqualTo(NOW.minus(Duration.ofMinutes(10)));
    }
}
