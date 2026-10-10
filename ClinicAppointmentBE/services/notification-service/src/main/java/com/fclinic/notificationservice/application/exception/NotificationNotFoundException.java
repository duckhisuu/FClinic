package com.fclinic.notificationservice.application.exception;

public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(Long id) {
        super("Notification " + id + " was not found");
    }
}
