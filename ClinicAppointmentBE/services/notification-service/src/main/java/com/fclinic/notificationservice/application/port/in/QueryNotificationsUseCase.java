package com.fclinic.notificationservice.application.port.in;

import com.fclinic.notificationservice.application.result.NotificationView;
import com.fclinic.notificationservice.domain.model.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface QueryNotificationsUseCase {

    Page<NotificationView> getForUser(Long userId, Pageable pageable);

    /** status may be null to list everything. */
    Page<NotificationView> getAll(NotificationStatus status, Pageable pageable);

    NotificationView getById(Long id);

    NotificationView retry(Long id);
}
