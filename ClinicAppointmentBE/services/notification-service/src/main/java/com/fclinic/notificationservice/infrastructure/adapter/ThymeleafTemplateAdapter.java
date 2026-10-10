package com.fclinic.notificationservice.infrastructure.adapter;

import com.fclinic.notificationservice.application.exception.TemplateNotFoundException;
import com.fclinic.notificationservice.application.port.out.TemplateRenderPort;
import com.fclinic.notificationservice.domain.model.NotificationChannel;
import com.fclinic.notificationservice.domain.model.NotificationType;
import com.fclinic.notificationservice.domain.model.RecipientType;
import com.fclinic.notificationservice.domain.valueobject.RenderedMessage;
import com.fclinic.notificationservice.infrastructure.entity.JpaNotificationTemplateEntity;
import com.fclinic.notificationservice.infrastructure.persistence.SpringDataJpaNotificationTemplateRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/** Renders DB-backed templates: subject as plain text, body as HTML (variables are HTML-escaped). */
@Component
public class ThymeleafTemplateAdapter implements TemplateRenderPort {

    private final SpringDataJpaNotificationTemplateRepository templateRepository;
    private final TemplateEngine htmlEngine;
    private final TemplateEngine textEngine;

    public ThymeleafTemplateAdapter(SpringDataJpaNotificationTemplateRepository templateRepository,
                                    @Qualifier("notificationHtmlTemplateEngine") TemplateEngine htmlEngine,
                                    @Qualifier("notificationTextTemplateEngine") TemplateEngine textEngine) {
        this.templateRepository = templateRepository;
        this.htmlEngine = htmlEngine;
        this.textEngine = textEngine;
    }

    @Override
    @Transactional(readOnly = true)
    public RenderedMessage render(NotificationType type, RecipientType recipientType, Map<String, Object> variables) {
        JpaNotificationTemplateEntity template = templateRepository
                .findByNotificationTypeAndRecipientTypeAndChannelAndActiveTrue(type, recipientType, NotificationChannel.EMAIL)
                .orElseThrow(() -> new TemplateNotFoundException(type, recipientType));

        Context context = new Context();
        context.setVariables(variables);

        String subject = textEngine.process(template.getSubjectTemplate(), context).replaceAll("\\s+", " ").trim();
        String body = htmlEngine.process(template.getBodyTemplate(), context);
        return new RenderedMessage(subject, body);
    }
}
