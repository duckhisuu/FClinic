package com.fclinic.notificationservice.application.port.out;

import com.fclinic.notificationservice.domain.model.NotificationType;
import com.fclinic.notificationservice.domain.model.RecipientType;
import com.fclinic.notificationservice.domain.valueobject.RenderedMessage;

import java.util.Map;

public interface TemplateRenderPort {

    RenderedMessage render(NotificationType type, RecipientType recipientType, Map<String, Object> variables);
}
