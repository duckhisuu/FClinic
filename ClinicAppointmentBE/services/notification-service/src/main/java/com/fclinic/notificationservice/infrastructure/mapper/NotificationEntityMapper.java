package com.fclinic.notificationservice.infrastructure.mapper;

import com.fclinic.notificationservice.domain.aggregate.Notification;
import com.fclinic.notificationservice.infrastructure.entity.JpaNotificationEntity;
import org.springframework.stereotype.Component;

@Component
public class NotificationEntityMapper {

    public Notification toDomain(JpaNotificationEntity e) {
        return Notification.builder()
                .id(e.getId()).eventId(e.getEventId()).appointmentId(e.getAppointmentId())
                .recipientUserId(e.getRecipientUserId()).recipientType(e.getRecipientType())
                .recipientName(e.getRecipientName()).recipientEmail(e.getRecipientEmail())
                .type(e.getType()).channel(e.getChannel()).subject(e.getSubject()).htmlBody(e.getHtmlBody())
                .status(e.getStatus()).attemptCount(e.getAttemptCount()).nextAttemptAt(e.getNextAttemptAt())
                .lastAttemptAt(e.getLastAttemptAt()).sentAt(e.getSentAt())
                .lastErrorCode(e.getLastErrorCode()).lastErrorMessage(e.getLastErrorMessage())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).version(e.getVersion())
                .build();
    }

    /** Applies every column to the entity; version is left to JPA so optimistic locking still applies. */
    public JpaNotificationEntity toEntity(Notification n, JpaNotificationEntity target) {
        target.setEventId(n.getEventId());
        target.setAppointmentId(n.getAppointmentId());
        target.setRecipientUserId(n.getRecipientUserId());
        target.setRecipientType(n.getRecipientType());
        target.setRecipientName(n.getRecipientName());
        target.setRecipientEmail(n.getRecipientEmail());
        target.setType(n.getType());
        target.setChannel(n.getChannel());
        target.setSubject(n.getSubject());
        target.setHtmlBody(n.getHtmlBody());
        target.setStatus(n.getStatus());
        target.setAttemptCount(n.getAttemptCount());
        target.setNextAttemptAt(n.getNextAttemptAt());
        target.setLastAttemptAt(n.getLastAttemptAt());
        target.setSentAt(n.getSentAt());
        target.setLastErrorCode(n.getLastErrorCode());
        target.setLastErrorMessage(n.getLastErrorMessage());
        target.setCreatedAt(n.getCreatedAt());
        target.setUpdatedAt(n.getUpdatedAt());
        return target;
    }
}
