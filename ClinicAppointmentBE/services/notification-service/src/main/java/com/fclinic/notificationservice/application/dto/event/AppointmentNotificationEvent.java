package com.fclinic.notificationservice.application.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fclinic.notificationservice.application.dto.AppointmentSnapshotDto;
import com.fclinic.notificationservice.application.dto.PreviousAppointmentSlotDto;
import com.fclinic.notificationservice.application.dto.RecipientDto;
import com.fclinic.notificationservice.domain.model.AppointmentEventType;
import com.fclinic.notificationservice.domain.model.RecipientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Inbound event contract published by the appointment lifecycle producer.
 * Supports both nested V1 schema and flattened V2 schema (with schemaVersion and recipients array).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppointmentNotificationEvent(
        @NotNull UUID eventId,
        @NotNull AppointmentEventType eventType,
        @NotNull Instant occurredAt,
        @NotBlank String source,
        AppointmentSnapshotDto appointment,
        RecipientDto patient,
        RecipientDto doctor,
        PreviousAppointmentSlotDto previousSlot,

        // Flattened V2 schema fields
        Integer schemaVersion,
        Long appointmentId,
        Long aggregateVersion,
        String bookingCode,
        String patientId,
        String patientName,
        String patientPhone,
        Long doctorId,
        String doctorName,
        String doctorSpecialty,
        Long slotId,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        String timezone,
        List<EventRecipientDto> recipients,
        Long previousSlotId,
        LocalDate previousAppointmentDate,
        LocalTime previousStartTime,
        LocalTime previousEndTime,
        Instant reminderDueAt
) {
    /** V1 8-parameter canonical constructor for backward compatibility and unit tests. */
    public AppointmentNotificationEvent(
            UUID eventId,
            AppointmentEventType eventType,
            Instant occurredAt,
            String source,
            AppointmentSnapshotDto appointment,
            RecipientDto patient,
            RecipientDto doctor,
            PreviousAppointmentSlotDto previousSlot
    ) {
        this(eventId, eventType, occurredAt, source, appointment, patient, doctor, previousSlot,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Resolves appointment snapshot from either V1 nested DTO or V2 flat fields. */
    public AppointmentSnapshotDto appointment() {
        if (appointment != null) {
            return appointment;
        }
        if (appointmentId != null && appointmentDate != null && startTime != null) {
            return new AppointmentSnapshotDto(
                    appointmentId,
                    appointmentDate,
                    startTime,
                    endTime,
                    "FClinic",
                    doctorSpecialty != null && !doctorSpecialty.isBlank() ? doctorSpecialty : "Phòng khám",
                    null,
                    null
            );
        }
        return null;
    }

    /** Resolves patient recipient from either V1 nested DTO or V2 recipients array / flat fields. */
    public RecipientDto patient() {
        if (patient != null) {
            return patient;
        }
        if (recipients != null) {
            for (var r : recipients) {
                if ("PATIENT".equalsIgnoreCase(r.recipientType())) {
                    Long uid = r.userId() != null ? r.userId() : 1L;
                    String email = r.email() != null ? r.email() : "";
                    String name = r.name() != null ? r.name() : (patientName != null ? patientName : "Bệnh nhân");
                    return new RecipientDto(uid, RecipientType.PATIENT, name, email);
                }
            }
        }
        if (patientName != null) {
            return new RecipientDto(1L, RecipientType.PATIENT, patientName, "");
        }
        return null;
    }

    /** Resolves doctor recipient from either V1 nested DTO or V2 recipients array / flat fields. */
    public RecipientDto doctor() {
        if (doctor != null) {
            return doctor;
        }
        if (recipients != null) {
            for (var r : recipients) {
                if ("DOCTOR".equalsIgnoreCase(r.recipientType())) {
                    Long uid = r.userId() != null ? r.userId() : (doctorId != null ? doctorId : 2L);
                    String email = r.email() != null ? r.email() : "";
                    String name = r.name() != null ? r.name() : (doctorName != null ? doctorName : "Bác sĩ");
                    return new RecipientDto(uid, RecipientType.DOCTOR, name, email);
                }
            }
        }
        if (doctorName != null) {
            return new RecipientDto(doctorId != null ? doctorId : 2L, RecipientType.DOCTOR, doctorName, "");
        }
        return null;
    }

    /** Resolves previous appointment slot from either V1 nested DTO or V2 flat fields. */
    public PreviousAppointmentSlotDto previousSlot() {
        if (previousSlot != null) {
            return previousSlot;
        }
        if (previousAppointmentDate != null && previousStartTime != null) {
            return new PreviousAppointmentSlotDto(previousAppointmentDate, previousStartTime);
        }
        return null;
    }
}
