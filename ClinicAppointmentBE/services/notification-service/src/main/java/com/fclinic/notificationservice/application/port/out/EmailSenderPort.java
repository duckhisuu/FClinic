package com.fclinic.notificationservice.application.port.out;

import com.fclinic.notificationservice.domain.valueobject.DeliveryResult;
import com.fclinic.notificationservice.domain.valueobject.EmailAddress;

public interface EmailSenderPort {

    /** Never throws; failures are reported through DeliveryResult. */
    DeliveryResult sendHtml(EmailAddress to, String recipientName, String subject, String htmlBody);
}
