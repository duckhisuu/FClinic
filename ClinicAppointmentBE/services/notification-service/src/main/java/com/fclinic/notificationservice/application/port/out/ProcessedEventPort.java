package com.fclinic.notificationservice.application.port.out;

import com.fclinic.notificationservice.application.dto.event.AppointmentNotificationEvent;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventPort {

    boolean exists(UUID eventId);

    /** Flushes immediately so a primary-key race surfaces as DataIntegrityViolationException. */
    void save(AppointmentNotificationEvent event, Instant processedAt);
}
