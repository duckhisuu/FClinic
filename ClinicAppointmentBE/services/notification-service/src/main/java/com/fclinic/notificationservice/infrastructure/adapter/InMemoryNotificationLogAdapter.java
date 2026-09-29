package com.fclinic.notificationservice.infrastructure.adapter;

import com.fclinic.notificationservice.application.port.out.NotificationLogRepositoryPort;
import com.fclinic.notificationservice.domain.aggregate.NotificationLog;
import com.fclinic.notificationservice.domain.repository.NotificationLogRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class InMemoryNotificationLogAdapter implements NotificationLogRepositoryPort, NotificationLogRepository {

    private final List<NotificationLog> logs = new CopyOnWriteArrayList<>();

    @Override
    public void save(NotificationLog log) {
        if (logs.size() >= 100) {
            logs.remove(0);
        }
        logs.add(log);
    }

    @Override
    public List<NotificationLog> findRecent(int limit) {
        List<NotificationLog> copy = new ArrayList<>(logs);
        Collections.reverse(copy);
        if (copy.size() > limit) {
            return copy.subList(0, limit);
        }
        return copy;
    }
}
