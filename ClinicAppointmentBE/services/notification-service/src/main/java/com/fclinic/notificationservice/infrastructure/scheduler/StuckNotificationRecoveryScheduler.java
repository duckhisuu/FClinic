package com.fclinic.notificationservice.infrastructure.scheduler;

import com.fclinic.notificationservice.application.port.in.DeliverNotificationUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StuckNotificationRecoveryScheduler {

    private final DeliverNotificationUseCase deliveryUseCase;

    public StuckNotificationRecoveryScheduler(DeliverNotificationUseCase deliveryUseCase) {
        this.deliveryUseCase = deliveryUseCase;
    }

    @Scheduled(fixedDelay = 60000)
    public void recover() {
        try {
            deliveryUseCase.recoverStuckNotifications();
        } catch (RuntimeException ex) {
            // e.g. optimistic-lock conflict with a worker that just finished; next run retries.
            log.warn("stuck_recovery_failed reason={}", ex.getMessage());
        }
    }
}
