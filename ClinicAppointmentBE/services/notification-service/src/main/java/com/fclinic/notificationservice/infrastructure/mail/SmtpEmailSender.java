package com.fclinic.notificationservice.infrastructure.mail;

import com.fclinic.notificationservice.application.port.out.EmailSenderPort;
import com.fclinic.notificationservice.config.NotificationProperties;
import com.fclinic.notificationservice.domain.valueobject.DeliveryResult;
import com.fclinic.notificationservice.domain.valueobject.EmailAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailSender implements EmailSenderPort {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public DeliveryResult sendHtml(EmailAddress to, String recipientName, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.getMail().getFromAddress(), properties.getMail().getFromName());
            helper.setTo(to.value());
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            return DeliveryResult.success(message.getMessageID());
        } catch (MailAuthenticationException ex) {
            return DeliveryResult.failure("SMTP_AUTH_ERROR", describe(ex));
        } catch (MailSendException ex) {
            return DeliveryResult.failure("SMTP_CONNECTION_ERROR", describe(ex));
        } catch (Exception ex) {
            return DeliveryResult.failure("UNKNOWN_EMAIL_ERROR", describe(ex));
        }
    }

    private static String describe(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
