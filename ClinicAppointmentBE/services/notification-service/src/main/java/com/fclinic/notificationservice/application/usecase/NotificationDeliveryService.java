package com.fclinic.notificationservice.application.usecase;

import com.fclinic.notificationservice.application.port.in.DeliverNotificationUseCase;
import com.fclinic.notificationservice.application.port.out.EmailSenderPort;
import com.fclinic.notificationservice.domain.aggregate.Notification;
import com.fclinic.notificationservice.domain.valueobject.DeliveryResult;
import com.fclinic.notificationservice.domain.valueobject.EmailAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Phase B: claim -> send (no DB transaction held) -> record outcome.
 */
@Slf4j
@Service
public class NotificationDeliveryService implements DeliverNotificationUseCase {

    private final NotificationStateService stateService;
    private final EmailSenderPort emailSender;

    public NotificationDeliveryService(NotificationStateService stateService, EmailSenderPort emailSender) {
        this.stateService = stateService;
        this.emailSender = emailSender;
    }

    @Override
    public void processDueNotifications() {
        for (Long id : stateService.findDueIds()) {
            try {
                deliver(id);
            } catch (RuntimeException ex) {
                log.error("delivery_unexpected_error notificationId={}", id, ex);
            }
        }
    }

    @Override
    public void deliver(Long id) {
        if (!stateService.claim(id)) {
            return;
        }
        Notification n = stateService.getRequired(id);
        log.info("delivery_started notificationId={} eventId={} appointmentId={} recipientUserId={} notificationType={}",
                id, n.getEventId(), n.getAppointmentId(), n.getRecipientUserId(), n.getType());

        DeliveryResult result;
        try {
            result = emailSender.sendHtml(new EmailAddress(n.getRecipientEmail()), n.getRecipientName(),
                    n.getSubject(), n.getHtmlBody());
        } catch (IllegalArgumentException ex) {
            result = DeliveryResult.failure("INVALID_RECIPIENT", ex.getMessage());
        }

        if (result.success()) {
            stateService.markSent(id);
        } else {
            stateService.recordFailure(id, result.errorCode(), result.errorMessage());
        }
    }

    @Override
    public int recoverStuckNotifications() {
        return stateService.recoverStuck();
    }
}
