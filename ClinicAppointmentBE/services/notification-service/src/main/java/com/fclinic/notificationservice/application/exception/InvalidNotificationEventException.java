package com.fclinic.notificationservice.application.exception;

public class InvalidNotificationEventException extends RuntimeException {
    public InvalidNotificationEventException(String message) {
        super(message);
    }
}
