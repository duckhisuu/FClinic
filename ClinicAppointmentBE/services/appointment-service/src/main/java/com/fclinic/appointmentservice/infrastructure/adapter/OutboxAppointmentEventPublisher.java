package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fclinic.appointmentservice.application.port.out.AppointmentEventPublisherPort;
import com.fclinic.appointmentservice.infrastructure.entity.JpaOutboxEventEntity;
import com.fclinic.appointmentservice.infrastructure.persistence.SpringDataJpaOutboxEventRepository;
import com.fclinic.common.events.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class OutboxAppointmentEventPublisher implements AppointmentEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(OutboxAppointmentEventPublisher.class);

    private final SpringDataJpaOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxAppointmentEventPublisher(
            SpringDataJpaOutboxEventRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            Instant now = Instant.now();

            JpaOutboxEventEntity entity = JpaOutboxEventEntity.pending(
                    event.eventId(),
                    String.valueOf(event.eventId()),
                    "APPOINTMENT",
                    event.eventType(),
                    payload,
                    now
            );

            outboxRepository.save(entity);
            log.info("[OutboxAppointmentEventPublisher] Stored outbox event id={} type={}",
                    event.eventId(), event.eventType());
        } catch (Exception e) {
            log.error("[OutboxAppointmentEventPublisher] Failed to serialize event type={}: {}",
                    event.eventType(), e.getMessage(), e);
            throw new RuntimeException("Lỗi lưu trữ outbox event: " + e.getMessage(), e);
        }
    }
}
