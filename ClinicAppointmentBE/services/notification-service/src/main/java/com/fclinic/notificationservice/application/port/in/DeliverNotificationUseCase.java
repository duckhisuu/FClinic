package com.fclinic.notificationservice.application.port.in;
import java.util.UUID;

public interface DeliverNotificationUseCase {

    void processDueNotifications();

    void deliver(UUID notificationId);

    int recoverStuckNotifications();
}
