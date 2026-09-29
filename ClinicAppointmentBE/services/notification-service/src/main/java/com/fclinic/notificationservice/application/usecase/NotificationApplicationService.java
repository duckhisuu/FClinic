package com.fclinic.notificationservice.application.usecase;

import com.fclinic.notificationservice.application.command.ProcessNotificationCommand;
import com.fclinic.notificationservice.application.port.in.ListNotificationsUseCase;
import com.fclinic.notificationservice.application.port.in.ProcessNotificationUseCase;
import com.fclinic.notificationservice.application.port.out.NotificationLogRepositoryPort;
import com.fclinic.notificationservice.application.result.NotificationView;
import com.fclinic.notificationservice.domain.aggregate.NotificationLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationApplicationService implements ProcessNotificationUseCase, ListNotificationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificationApplicationService.class);

    private final NotificationLogRepositoryPort repository;

    public NotificationApplicationService(NotificationLogRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public NotificationView processNotification(ProcessNotificationCommand command) {
        NotificationLog notificationLog = NotificationLog.create(
                command.bookingCode(),
                command.recipientName(),
                command.recipientPhone(),
                command.doctorName(),
                command.appointmentTime(),
                command.channel(),
                command.message()
        );

        repository.save(notificationLog);

        log.info("[NotificationApplicationService] Processed notification for bookingCode={} recipient={} ({})",
                command.bookingCode(), command.recipientName(), command.recipientPhone());

        return NotificationView.from(notificationLog);
    }

    @Override
    public List<NotificationView> getRecentNotifications(int limit) {
        return repository.findRecent(limit).stream()
                .map(NotificationView::from)
                .toList();
    }
}
