package com.fclinic.appointmentservice.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Legacy operations projection: excludes intent, recipients and final payload. */
public record OutboxEventResponse(
    UUID id,
    long aggregateId,
    String eventType,
    String status,
    int attemptCount,
    Instant publishedAt) {}
