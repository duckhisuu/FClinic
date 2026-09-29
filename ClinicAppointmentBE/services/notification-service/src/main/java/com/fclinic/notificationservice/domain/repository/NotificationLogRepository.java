package com.fclinic.notificationservice.domain.repository;

import com.fclinic.notificationservice.domain.aggregate.NotificationLog;
import java.util.List;

public interface NotificationLogRepository {
    void save(NotificationLog log);
    List<NotificationLog> findRecent(int limit);
}
