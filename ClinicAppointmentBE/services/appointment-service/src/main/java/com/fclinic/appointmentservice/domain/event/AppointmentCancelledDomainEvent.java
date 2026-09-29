package com.fclinic.appointmentservice.domain.event;

import com.fclinic.common.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record AppointmentCancelledDomainEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        Long appointmentId,
        String bookingCode,
        String patientPhone,
        String reason
) implements DomainEvent {

    public static AppointmentCancelledDomainEvent from(Long appointmentId, String bookingCode, String patientPhone, String reason) {
        return new AppointmentCancelledDomainEvent(
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
