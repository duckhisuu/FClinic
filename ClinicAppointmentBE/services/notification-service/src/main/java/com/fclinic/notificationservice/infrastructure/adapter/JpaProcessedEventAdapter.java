package com.fclinic.notificationservice.infrastructure.adapter;

import com.fclinic.notificationservice.application.dto.event.AppointmentNotificationEvent;
import com.fclinic.notificationservice.application.port.out.ProcessedEventPort;
import com.fclinic.notificationservice.infrastructure.entity.JpaProcessedEventEntity;
import com.fclinic.notificationservice.infrastructure.persistence.SpringDataJpaProcessedEventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class JpaProcessedEventAdapter implements ProcessedEventPort {

    private final SpringDataJpaProcessedEventRepository repository;

    public JpaProcessedEventAdapter(SpringDataJpaProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean exists(UUID eventId) {
        return repository.existsById(eventId);
    }

    @Override
    public void save(AppointmentNotificationEvent event, Instant processedAt) {
        // Flush now: for an assigned id Spring Data merges (select, then insert), and a lost race must fail here.
        repository.saveAndFlush(new JpaProcessedEventEntity(event.eventId(), event.eventType(),
                event.source(), event.occurredAt(), processedAt));
    }
}
