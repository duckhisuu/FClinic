package com.fclinic.notificationservice.application.usecase;

import com.fclinic.notificationservice.application.exception.NotificationNotFoundException;
import com.fclinic.notificationservice.application.exception.NotificationNotRetryableException;
import com.fclinic.notificationservice.application.port.out.NotificationRepositoryPort;
import com.fclinic.notificationservice.config.NotificationProperties;
import com.fclinic.notificationservice.domain.aggregate.Notification;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Short, transaction-scoped state transitions. Performs no SMTP/network I/O. Kept as a separate bean so
 * the delivery service calls it through the Spring proxy.
 */
@Slf4j
@Service
public class NotificationStateService {

    private static final List<com.fclinic.notificationservice.domain.model.NotificationStatus> CLAIMABLE = List.of(
            com.fclinic.notificationservice.domain.model.NotificationStatus.PENDING,
            com.fclinic.notificationservice.domain.model.NotificationStatus.RETRY_SCHEDULED);

    private final NotificationRepositoryPort repository;
    private final NotificationProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public NotificationStateService(NotificationRepositoryPort repository, NotificationProperties properties,
                                    Clock clock, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public List<Long> findDueIds() {
        return repository.findDueIds(CLAIMABLE, clock.instant(), properties.getDelivery().getBatchSize());
    }

    @Transactional
    public boolean claim(Long id) {
        return repository.claimForProcessing(id, clock.instant());
    }

    @Transactional(readOnly = true)
    public Notification getRequired(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotificationNotFoundException(id));
    }

    @Transactional
    public void markSent(Long id) {
        Notification n = getRequired(id);
        n.markSent(clock.instant());
        repository.save(n);
        counter("notification.delivery.sent", n).increment();
        log.info("delivery_sent notificationId={} eventId={} appointmentId={}", id, n.getEventId(), n.getAppointmentId());
    }

    @Transactional
    public void recordFailure(Long id, String errorCode, String errorMessage) {
        Notification n = getRequired(id);
        Instant now = clock.instant();
        NotificationProperties.Delivery cfg = properties.getDelivery();
        int failedSoFar = n.getAttemptCount() + 1;

        if (failedSoFar >= cfg.getMaxAttempts()) {
            n.markPermanentFailure(errorCode, errorMessage, now);
            counter("notification.delivery.permanent_failure", n).increment();
            log.error("delivery_permanent_failure notificationId={} attempt={} errorCode={}", id, failedSoFar, errorCode);
        } else {
            Duration delay = retryDelay(n.getAttemptCount());
            n.scheduleRetry(now.plus(delay), errorCode, errorMessage, now);
            counter("notification.delivery.retry", n).increment();
            log.warn("delivery_retry notificationId={} attempt={} nextAttemptAt={} errorCode={}",
                    id, failedSoFar, n.getNextAttemptAt(), errorCode);
        }
        repository.save(n);
    }

    @Transactional
    public int recoverStuck() {
        Instant now = clock.instant();
        Instant staleBefore = now.minus(properties.getDelivery().getProcessingTimeout());
        int recovered = 0;
        for (Notification n : repository.findStuckProcessing(staleBefore, properties.getDelivery().getBatchSize())) {
            n.recoverFromStuckProcessing(now);
            repository.save(n);
            counter("notification.delivery.recovered_stuck", n).increment();
            log.warn("delivery_recovered_stuck notificationId={}", n.getId());
            recovered++;
        }
        return recovered;
    }

    @Transactional
    public Notification manualRetry(Long id) {
        Notification n = getRequired(id);
        if (!n.isManuallyRetryable()) {
            throw new NotificationNotRetryableException(id, n.getStatus());
        }
        n.resetForManualRetry(clock.instant());
        return repository.save(n);
    }

    /** retryDelays[i] applies after the (i+1)-th failure; the last entry repeats if the list is shorter. */
    Duration retryDelay(int failedAttemptsBefore) {
        List<Duration> delays = properties.getDelivery().getRetryDelays();
        if (delays.isEmpty()) {
            return Duration.ofMinutes(1);
        }
        return delays.get(Math.min(failedAttemptsBefore, delays.size() - 1));
    }

    private io.micrometer.core.instrument.Counter counter(String name, Notification n) {
        return meterRegistry.counter(name, "notificationType", n.getType().name(),
                "recipientType", n.getRecipientType().name());
    }
}
