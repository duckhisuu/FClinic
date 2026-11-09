package com.fclinic.notificationservice.application.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/** Recipient representation in schema version 3 events from appointment-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventRecipientDto(
        String recipientType,
        UUID userId,
        String name,
        String email
) {
}
