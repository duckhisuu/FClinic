package com.fclinic.appointmentservice.domain.event;

import com.fclinic.common.events.DomainEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentBookedDomainEvent(
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
        Long slotId,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        String reasonForVisit,
        Double consultationFee
) implements DomainEvent {

    public static AppointmentBookedDomainEvent from(
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
            Long slotId,
            LocalDate appointmentDate,
            LocalTime startTime,
            LocalTime endTime,
            String reasonForVisit,
            Double consultationFee
    ) {
        return new AppointmentBookedDomainEvent(
                UUID.randomUUID(),
                "APPOINTMENT_BOOKED",
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
                slotId,
                appointmentDate,
                startTime,
                endTime,
                reasonForVisit,
                consultationFee
        );
    }

    public static AppointmentBookedDomainEvent from(
            Long appointmentId,
            String bookingCode,
            Long patientId,
            String patientName,
            String patientPhone,
            Long doctorId,
            String doctorName,
            String doctorSpecialty,
            Long slotId,
            LocalDate appointmentDate,
            LocalTime startTime,
            LocalTime endTime,
            String reasonForVisit,
            Double consultationFee
    ) {
        return from(appointmentId, bookingCode, patientId, patientName, patientPhone, null,
                doctorId, doctorName, doctorSpecialty, null, slotId, appointmentDate, startTime, endTime, reasonForVisit, consultationFee);
    }
}
