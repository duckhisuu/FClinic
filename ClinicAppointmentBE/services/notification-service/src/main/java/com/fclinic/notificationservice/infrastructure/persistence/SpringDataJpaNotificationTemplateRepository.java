package com.fclinic.notificationservice.infrastructure.persistence;

import com.fclinic.notificationservice.domain.model.NotificationChannel;
import com.fclinic.notificationservice.domain.model.NotificationType;
import com.fclinic.notificationservice.domain.model.RecipientType;
import com.fclinic.notificationservice.infrastructure.entity.JpaNotificationTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataJpaNotificationTemplateRepository extends JpaRepository<JpaNotificationTemplateEntity, Long> {

    Optional<JpaNotificationTemplateEntity> findByNotificationTypeAndRecipientTypeAndChannelAndActiveTrue(
            NotificationType type, RecipientType recipientType, NotificationChannel channel);
}
