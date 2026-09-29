package com.fclinic.appointmentservice.infrastructure.entity;

import com.fclinic.common.web.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_events_status", columnList = "status")
})
public class JpaOutboxEventEntity extends BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected JpaOutboxEventEntity() {
    }

    private JpaOutboxEventEntity(
            UUID id,
            String aggregateId,
            String aggregateType,
            String eventType,
            String payload,
            String status,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        super(createdAt, updatedAt);
        this.id = id;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.publishedAt = publishedAt;
    }

    public static JpaOutboxEventEntity pending(
            UUID eventId,
            String aggregateId,
            String aggregateType,
            String eventType,
            String payload,
            Instant now
    ) {
        return new JpaOutboxEventEntity(eventId, aggregateId, aggregateType, eventType, payload, "PENDING", null, now, now);
    }

    public void markCompleted(Instant now) {
        this.status = "COMPLETED";
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
