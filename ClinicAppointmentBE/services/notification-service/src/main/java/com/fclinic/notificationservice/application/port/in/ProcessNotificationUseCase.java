package com.fclinic.notificationservice.application.port.in;

import com.fclinic.notificationservice.application.command.ProcessNotificationCommand;
import com.fclinic.notificationservice.application.result.NotificationView;

public interface ProcessNotificationUseCase {
    NotificationView processNotification(ProcessNotificationCommand command);
}
