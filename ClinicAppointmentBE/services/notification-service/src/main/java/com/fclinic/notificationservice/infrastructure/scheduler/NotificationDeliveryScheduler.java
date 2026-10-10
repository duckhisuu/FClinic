package com.fclinic.notificationservice.infrastructure.scheduler;

import com.fclinic.notificationservice.application.port.in.DeliverNotificationUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationDeliveryScheduler {

    private final DeliverNotificationUseCase deliveryUseCase;

    public NotificationDeliveryScheduler(DeliverNotificationUseCase deliveryUseCase) {
        this.deliveryUseCase = deliveryUseCase;
    }

    @Scheduled(fixedDelayString = "${app.notification.delivery.scheduler-delay-ms:5000}")
    public void deliverDueNotifications() {
        deliveryUseCase.processDueNotifications();
    }
}
