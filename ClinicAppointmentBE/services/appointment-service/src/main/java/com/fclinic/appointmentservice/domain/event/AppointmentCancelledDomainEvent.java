package com.fclinic.appointmentservice.domain.event;

import com.fclinic.common.events.DomainEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentCancelledDomainEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        Long appointmentId,
        String bookingCode,
        Long patientId,
        String patientName,
        String patientPhone,
        String patientEmail,
        Long doctorId,
        String doctorName,
        String doctorSpecialty,
        String doctorEmail,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        String reason
) implements DomainEvent {

    public static AppointmentCancelledDomainEvent from(
            Long appointmentId,
            String bookingCode,
            Long patientId,
            String patientName,
            String patientPhone,
            String patientEmail,
            Long doctorId,
            String doctorName,
            String doctorSpecialty,
            String doctorEmail,
            LocalDate appointmentDate,
            LocalTime startTime,
            LocalTime endTime,
            String reason
    ) {
        return new AppointmentCancelledDomainEvent(
                UUID.randomUUID(),
                "APPOINTMENT_CANCELLED",
                Instant.now(),
                appointmentId,
                bookingCode,
                patientId,
                patientName,
                patientPhone,
                patientEmail,
                doctorId,
                doctorName,
                doctorSpecialty,
                doctorEmail,
                appointmentDate,
                startTime,
                endTime,
                reason
        );
    }

    public static AppointmentCancelledDomainEvent from(Long appointmentId, String bookingCode, String patientPhone, String reason) {
        return from(appointmentId, bookingCode, null, null, patientPhone, null,
                null, null, null, null, null, null, null, reason);
    }
}
