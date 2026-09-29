package com.fclinic.appointmentservice.api.dto;

import com.fclinic.appointmentservice.infrastructure.entity.JpaOutboxEventEntity;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventResponse(
        UUID id,
        String aggregateId,
        String aggregateType,
        String eventType,
        String payload,
        String status,
        Instant publishedAt,
        Instant createdAt
) {
    public static OutboxEventResponse from(JpaOutboxEventEntity entity) {
        return new OutboxEventResponse(
                entity.getId(),
                entity.getAggregateId(),
                entity.getAggregateType(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getStatus(),
                entity.getPublishedAt(),
                entity.getCreatedAt()
        );
    }
}
