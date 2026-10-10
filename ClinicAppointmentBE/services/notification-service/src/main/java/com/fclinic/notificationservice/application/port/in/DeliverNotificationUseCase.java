package com.fclinic.notificationservice.application.port.in;

public interface DeliverNotificationUseCase {

    void processDueNotifications();

    void deliver(Long notificationId);

    int recoverStuckNotifications();
}
