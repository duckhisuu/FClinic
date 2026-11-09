package com.fclinic.notificationservice.application.exception;
import java.util.UUID;

import com.fclinic.notificationservice.domain.model.NotificationStatus;

public class NotificationNotRetryableException extends RuntimeException {
    public NotificationNotRetryableException(UUID id, NotificationStatus status) {
        super("Notification " + id + " cannot be retried in status " + status);
    }
}
