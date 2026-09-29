package com.fclinic.notificationservice.application.port.in;

import com.fclinic.notificationservice.application.result.NotificationView;
import java.util.List;

public interface ListNotificationsUseCase {
    List<NotificationView> getRecentNotifications(int limit);
}
