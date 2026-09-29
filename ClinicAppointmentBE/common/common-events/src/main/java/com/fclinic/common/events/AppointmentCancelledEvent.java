package com.fclinic.common.events;

import java.time.Instant;
import java.util.UUID;

public record AppointmentCancelledEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        Long appointmentId,
        String bookingCode,
        String patientPhone,
        String reason
) implements DomainEvent {

    public static AppointmentCancelledEvent create(Long appointmentId, String bookingCode, String patientPhone, String reason) {
        return new AppointmentCancelledEvent(
                UUID.randomUUID(),
                "APPOINTMENT_CANCELLED",
                Instant.now(),
                appointmentId,
                bookingCode,
                patientPhone,
                reason
        );
    }
}
