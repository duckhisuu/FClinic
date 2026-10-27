package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fclinic.appointmentservice.infrastructure.entity.JpaOutboxEventEntity;
import com.fclinic.appointmentservice.infrastructure.persistence.SpringDataJpaOutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxRelayJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayJob.class);

    private final SpringDataJpaOutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String defaultRoutingKey;
    private final int batchSize;

    public OutboxRelayJob(
            SpringDataJpaOutboxEventRepository repository,
            RabbitTemplate rabbitTemplate,
            @Value("${fclinic.rabbitmq.exchange:fclinic.direct.exchange}") String exchange,
            @Value("${fclinic.rabbitmq.routingkey:appointment.booked}") String defaultRoutingKey,
            @Value("${outbox.relay.batch-size:50}") int batchSize
    ) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.defaultRoutingKey = defaultRoutingKey;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:3000}")
    @Transactional
    public void relay() {
        List<JpaOutboxEventEntity> pending = repository.findPendingForUpdate(batchSize);
        if (pending.isEmpty()) {
            return;
        }

        log.info("[OutboxRelay][appointment-service] Found {} pending outbox event(s) to publish", pending.size());

        for (JpaOutboxEventEntity event : pending) {
            try {
                String routingKey = determineRoutingKey(event.getEventType());
                rabbitTemplate.convertAndSend(exchange, routingKey, event.getPayload());

                event.markCompleted(Instant.now());
                repository.save(event);

                log.info("[OutboxRelay][appointment-service] Successfully published event id={} type={} to exchange={} routingKey={}",
                        event.getId(), event.getEventType(), exchange, routingKey);
            } catch (Exception ex) {
                log.error("[OutboxRelay][appointment-service] Failed to publish event id={} type={}: {}",
                        event.getId(), event.getEventType(), ex.getMessage(), ex);
            }
        }
    }

    private String determineRoutingKey(String eventType) {
        if ("APPOINTMENT_CANCELLED".equalsIgnoreCase(eventType)) {
            return "appointment.cancelled";
        }
        return defaultRoutingKey;
    }
}
