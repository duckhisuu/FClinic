package com.fclinic.notificationservice.application.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Recipient representation in schema version 2 events from appointment-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventRecipientDto(
        String recipientType,
        Long userId,
        String name,
        String email
) {
}
