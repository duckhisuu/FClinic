package com.fclinic.appointmentservice.infrastructure.entity;

import com.fclinic.common.web.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class JpaOutboxEventEntity extends BaseEntity {
  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "aggregate_id", nullable = false)
  private Long aggregateId;

  @Column(name = "aggregate_version", nullable = false)
  private long aggregateVersion;

  @Column(name = "event_type", nullable = false, length = 40)
  private String eventType;

  @Column(nullable = false, columnDefinition = "jsonb")
  @ColumnTransformer(write = "?::jsonb")
  private String intent;

  @Column(columnDefinition = "text")
  private String payload;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "last_error_code", length = 100)
  private String lastErrorCode;

  @Column(name = "claim_token")
  private UUID claimToken;

  @Column(name = "claimed_until")
  private Instant claimedUntil;

  @Column(name = "published_at")
  private Instant publishedAt;

  public static JpaOutboxEventEntity pending(
      UUID eventId,
      long appointmentId,
      long version,
      String eventType,
      String intent,
      Instant now) {
    var row = new JpaOutboxEventEntity();
    row.setId(eventId);
    row.setAggregateId(appointmentId);
    row.setAggregateVersion(version);
    row.setEventType(eventType);
    row.setIntent(intent);
    row.setStatus("PENDING_ENRICHMENT");
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    return row;
  }
}
