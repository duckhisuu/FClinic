package com.fclinic.notificationservice.application.port.out;

import com.fclinic.notificationservice.domain.aggregate.NotificationLog;
import java.util.List;

public interface NotificationLogRepositoryPort {
    void save(NotificationLog log);
    List<NotificationLog> findRecent(int limit);
}
