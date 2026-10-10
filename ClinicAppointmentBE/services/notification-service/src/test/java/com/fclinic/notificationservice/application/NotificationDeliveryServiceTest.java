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

    private NotificationStateService stateService;
    private EmailSenderPort emailSender;
    private NotificationDeliveryService service;

    @BeforeEach
    void setUp() {
        stateService = mock(NotificationStateService.class);
        emailSender = mock(EmailSenderPort.class);
        service = new NotificationDeliveryService(stateService, emailSender);
        Notification n = Notification.builder().id(1L).eventId(UUID.randomUUID()).appointmentId(5L)
                .recipientUserId(2L).recipientType(RecipientType.PATIENT).recipientName("A")
                .recipientEmail("a@x.com").subject("s").htmlBody("b")
                .type(NotificationType.APPOINTMENT_CONFIRMATION).build();
        when(stateService.getRequired(1L)).thenReturn(n);
    }

    @Test
    void doesNotSendWhenClaimLost() {
        when(stateService.claim(1L)).thenReturn(false);
        service.deliver(1L);
        verify(emailSender, never()).sendHtml(any(), any(), any(), any());
    }

    @Test
    void marksSentOnSuccess() {
        when(stateService.claim(1L)).thenReturn(true);
        when(emailSender.sendHtml(any(), any(), any(), any())).thenReturn(DeliveryResult.success("id"));
        service.deliver(1L);
        verify(stateService).markSent(1L);
    }

    @Test
    void recordsFailureOnSmtpError() {
        when(stateService.claim(1L)).thenReturn(true);
        when(emailSender.sendHtml(any(), any(), any(), any())).thenReturn(DeliveryResult.failure("SMTP_CONNECTION_ERROR", "down"));
        service.deliver(1L);
        verify(stateService).recordFailure(eq(1L), eq("SMTP_CONNECTION_ERROR"), eq("down"));
        verify(stateService, never()).markSent(any());
    }
}
