package com.fclinic.notificationservice.application.port.in;

import com.fclinic.notificationservice.application.result.NotificationView;
import com.fclinic.notificationservice.domain.model.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface QueryNotificationsUseCase {

    Page<NotificationView> getForUser(UUID userId, Pageable pageable);

    NotificationView getForUserById(UUID userId, UUID id);

    /** status may be null to list everything. */
    Page<NotificationView> getAll(NotificationStatus status, Pageable pageable);

    NotificationView getById(UUID id);

    NotificationView retry(UUID id);
}
