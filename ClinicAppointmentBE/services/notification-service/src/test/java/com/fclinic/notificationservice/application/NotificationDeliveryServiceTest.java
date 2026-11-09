package com.fclinic.notificationservice.application;

import com.fclinic.notificationservice.application.port.out.EmailSenderPort;
import com.fclinic.notificationservice.application.usecase.NotificationDeliveryService;
import com.fclinic.notificationservice.application.usecase.NotificationStateService;
import com.fclinic.notificationservice.domain.aggregate.Notification;
import com.fclinic.notificationservice.domain.model.NotificationType;
import com.fclinic.notificationservice.domain.model.RecipientType;
import com.fclinic.notificationservice.domain.valueobject.DeliveryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryServiceTest {
    private static final UUID ID = UUID.randomUUID();

    private NotificationStateService stateService;
    private EmailSenderPort emailSender;
    private NotificationDeliveryService service;

    @BeforeEach
    void setUp() {
        stateService = mock(NotificationStateService.class);
        emailSender = mock(EmailSenderPort.class);
        service = new NotificationDeliveryService(stateService, emailSender);
        Notification n = Notification.builder().id(ID).eventId(UUID.randomUUID()).appointmentId(UUID.randomUUID())
                .recipientUserId(UUID.randomUUID()).recipientType(RecipientType.PATIENT).recipientName("A")
                .recipientEmail("a@x.com").subject("s").htmlBody("b")
                .type(NotificationType.APPOINTMENT_CONFIRMATION).build();
        when(stateService.getRequired(ID)).thenReturn(n);
    }

    @Test
    void doesNotSendWhenClaimLost() {
        when(stateService.claim(ID)).thenReturn(false);
        service.deliver(ID);
        verify(emailSender, never()).sendHtml(any(), any(), any(), any());
    }

    @Test
    void marksSentOnSuccess() {
        when(stateService.claim(ID)).thenReturn(true);
        when(emailSender.sendHtml(any(), any(), any(), any())).thenReturn(DeliveryResult.success("id"));
        service.deliver(ID);
        verify(stateService).markSent(ID);
    }

    @Test
    void recordsFailureOnSmtpError() {
        when(stateService.claim(ID)).thenReturn(true);
        when(emailSender.sendHtml(any(), any(), any(), any())).thenReturn(DeliveryResult.failure("SMTP_CONNECTION_ERROR", "down"));
        service.deliver(ID);
        verify(stateService).recordFailure(eq(ID), eq("SMTP_CONNECTION_ERROR"), eq("down"));
        verify(stateService, never()).markSent(any());
    }
}
