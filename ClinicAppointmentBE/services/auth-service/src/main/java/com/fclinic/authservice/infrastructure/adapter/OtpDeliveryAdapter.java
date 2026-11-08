package com.fclinic.authservice.infrastructure.adapter;

import com.fclinic.authservice.application.port.out.OtpDeliveryPort;
import com.fclinic.authservice.infrastructure.config.OtpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class OtpDeliveryAdapter implements OtpDeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(OtpDeliveryAdapter.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final OtpProperties otpProperties;

    public OtpDeliveryAdapter(ObjectProvider<JavaMailSender> mailSenderProvider, OtpProperties otpProperties) {
        this.mailSenderProvider = mailSenderProvider;
        this.otpProperties = otpProperties;
    }

    @Override
    public void sendPasswordResetOtp(String email, String fullName, String otpCode, long expiresInSeconds) {
        if (!otpProperties.mailEnabled()) {
            log.info("[OTP] Mail delivery disabled; OTP issued for email={} (ttl={}s)", email, expiresInSeconds);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("[OTP] JavaMailSender bean unavailable; skipping SMTP send for email={}", email);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(otpProperties.fromAddress());
            message.setTo(email);
            message.setSubject("[FClinic] Mã OTP khôi phục mật khẩu");
            message.setText("Xin chào " + fullName + ",\n\n"
                    + "Mã OTP đặt lại mật khẩu FClinic của bạn là: " + otpCode + "\n"
                    + "Mã có hiệu lực trong " + (expiresInSeconds / 60) + " phút.\n\n"
                    + "Nếu bạn không yêu cầu thao tác này, vui lòng bỏ qua email.");
            sender.send(message);
            log.info("[OTP] Password reset email dispatched via SMTP to {}", email);
        } catch (MailException ex) {
            log.warn("[OTP] Failed to dispatch SMTP email to {}: {}", email, ex.getMessage());
        }
    }
}

