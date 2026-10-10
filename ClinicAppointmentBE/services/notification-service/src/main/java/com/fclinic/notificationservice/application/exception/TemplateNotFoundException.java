package com.fclinic.notificationservice.application.exception;

import com.fclinic.notificationservice.domain.model.NotificationType;
import com.fclinic.notificationservice.domain.model.RecipientType;

public class TemplateNotFoundException extends RuntimeException {
    public TemplateNotFoundException(NotificationType type, RecipientType recipientType) {
        super("No active EMAIL template for " + type + " / " + recipientType);
    }
}
