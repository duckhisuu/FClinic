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
 * Consumes UUID identities from appointment schema version 3. No inferred recipient accounts.
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

        // Flattened schema version 3 fields
        Integer schemaVersion,
        UUID appointmentId,
        Long aggregateVersion,
        String bookingCode,
        String patientId,
        String patientName,
        String patientPhone,
        UUID doctorId,
        String doctorName,
        String doctorSpecialty,
        UUID slotId,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        String timezone,
        List<EventRecipientDto> recipients,
        UUID previousSlotId,
        LocalDate previousAppointmentDate,
        LocalTime previousStartTime,
        LocalTime previousEndTime,
        Instant reminderDueAt
) {
    public boolean hasNestedRecipients() {
        return patient != null || doctor != null;
    }

    /** Convenience constructor for a UUID notification snapshot. */
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
                3, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Resolves the appointment snapshot without changing its identity. */
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

    /** A walk-in patient may have no account; never substitute another user's ID. */
    public RecipientDto patient() {
        if (patient != null) {
            return patient;
        }
        if (recipients != null) {
            for (var r : recipients) {
                if ("PATIENT".equalsIgnoreCase(r.recipientType())) {
                    UUID uid = r.userId();
                    String email = r.email() != null ? r.email() : "";
                    String name = r.name() != null ? r.name() : (patientName != null ? patientName : "Bệnh nhân");
                    return new RecipientDto(uid, RecipientType.PATIENT, name, email);
                }
            }
        }
        return null;
    }

    /** The doctor account must be supplied explicitly by the producer. */
    public RecipientDto doctor() {
        if (doctor != null) {
            return doctor;
        }
        if (recipients != null) {
            for (var r : recipients) {
                if ("DOCTOR".equalsIgnoreCase(r.recipientType())) {
                    UUID uid = r.userId();
                    String email = r.email() != null ? r.email() : "";
                    String name = r.name() != null ? r.name() : (doctorName != null ? doctorName : "Bác sĩ");
                    return new RecipientDto(uid, RecipientType.DOCTOR, name, email);
                }
            }
        }
        return null;
    }

    /** Resolves the previous slot for rescheduling messages. */
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
