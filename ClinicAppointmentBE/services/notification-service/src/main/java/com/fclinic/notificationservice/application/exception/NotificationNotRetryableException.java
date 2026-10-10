package com.fclinic.notificationservice.application.exception;

import com.fclinic.notificationservice.domain.model.NotificationStatus;

public class NotificationNotRetryableException extends RuntimeException {
    public NotificationNotRetryableException(Long id, NotificationStatus status) {
        super("Notification " + id + " cannot be retried in status " + status);
    }
}
