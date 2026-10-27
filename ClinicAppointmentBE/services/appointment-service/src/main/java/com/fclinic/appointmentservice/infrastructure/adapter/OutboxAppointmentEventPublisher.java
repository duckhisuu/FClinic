package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fclinic.appointmentservice.application.port.out.AppointmentEventPublisherPort;
import com.fclinic.appointmentservice.domain.event.AppointmentBookedDomainEvent;
import com.fclinic.appointmentservice.domain.event.AppointmentCancelledDomainEvent;
import com.fclinic.appointmentservice.infrastructure.entity.JpaOutboxEventEntity;
import com.fclinic.appointmentservice.infrastructure.messaging.dto.AppointmentNotificationPayload;
import com.fclinic.appointmentservice.infrastructure.persistence.SpringDataJpaOutboxEventRepository;
import com.fclinic.common.events.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Component
public class OutboxAppointmentEventPublisher implements AppointmentEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(OutboxAppointmentEventPublisher.class);

    private final SpringDataJpaOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxAppointmentEventPublisher(
            SpringDataJpaOutboxEventRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(DomainEvent event) {
        try {
            String payload;
            String eventType;

            if (event instanceof AppointmentBookedDomainEvent booked) {
                eventType = "APPOINTMENT_CREATED";
                AppointmentNotificationPayload notificationPayload = new AppointmentNotificationPayload(
                        booked.eventId(),
                        "APPOINTMENT_CREATED",
                        booked.occurredAt(),
                        "appointment-service",
                        new AppointmentNotificationPayload.AppointmentSnapshotPayload(
                                booked.appointmentId(),
                                booked.appointmentDate(),
                                booked.startTime(),
                                booked.endTime(),
                                "FClinic",
                                "Phòng khám " + (booked.slotId() != null ? booked.slotId() : "101"),
                                booked.reasonForVisit(),
                                null
                        ),
                        new AppointmentNotificationPayload.RecipientPayload(
                                booked.patientId(),
                                "PATIENT",
                                booked.patientName(),
                                resolveEmail(booked.patientEmail(), (booked.patientPhone() != null ? booked.patientPhone() : "patient" + booked.patientId()) + "@fclinic.local")
                        ),
                        new AppointmentNotificationPayload.RecipientPayload(
                                booked.doctorId(),
                                "DOCTOR",
                                booked.doctorName(),
                                resolveEmail(booked.doctorEmail(), "doctor" + booked.doctorId() + "@fclinic.vn")
                        ),
                        null
                );
                payload = objectMapper.writeValueAsString(notificationPayload);
            } else if (event instanceof AppointmentCancelledDomainEvent cancelled) {
                eventType = "APPOINTMENT_CANCELLED";
                AppointmentNotificationPayload notificationPayload = new AppointmentNotificationPayload(
                        cancelled.eventId(),
                        "APPOINTMENT_CANCELLED",
                        cancelled.occurredAt(),
                        "appointment-service",
                        new AppointmentNotificationPayload.AppointmentSnapshotPayload(
                                cancelled.appointmentId(),
                                cancelled.appointmentDate() != null ? cancelled.appointmentDate() : LocalDate.now(),
                                cancelled.startTime() != null ? cancelled.startTime() : LocalTime.of(8, 0),
                                cancelled.endTime() != null ? cancelled.endTime() : LocalTime.of(8, 30),
                                "FClinic",
                                "Phòng khám 101",
                                "Lịch hẹn khám",
                                cancelled.reason()
                        ),
                        new AppointmentNotificationPayload.RecipientPayload(
                                cancelled.patientId() != null ? cancelled.patientId() : 1L,
                                "PATIENT",
                                cancelled.patientName() != null ? cancelled.patientName() : "Bệnh nhân",
                                resolveEmail(cancelled.patientEmail(), (cancelled.patientPhone() != null ? cancelled.patientPhone() : "0900000001") + "@fclinic.local")
                        ),
                        new AppointmentNotificationPayload.RecipientPayload(
                                cancelled.doctorId() != null ? cancelled.doctorId() : 1L,
                                "DOCTOR",
                                cancelled.doctorName() != null ? cancelled.doctorName() : "Bác sĩ",
                                resolveEmail(cancelled.doctorEmail(), "doctor" + (cancelled.doctorId() != null ? cancelled.doctorId() : 1L) + "@fclinic.vn")
                        ),
                        null
                );
                payload = objectMapper.writeValueAsString(notificationPayload);
            } else {
                eventType = event.eventType();
                payload = objectMapper.writeValueAsString(event);
            }

            Instant now = Instant.now();
            JpaOutboxEventEntity entity = JpaOutboxEventEntity.pending(
                    event.eventId(),
                    String.valueOf(event.eventId()),
                    "APPOINTMENT",
                    eventType,
                    payload,
                    now
            );

            outboxRepository.save(entity);
            log.info("[OutboxAppointmentEventPublisher] Stored outbox event id={} type={}",
                    event.eventId(), eventType);
        } catch (Exception e) {
            log.error("[OutboxAppointmentEventPublisher] Failed to serialize event type={}: {}",
                    event.eventType(), e.getMessage(), e);
            throw new RuntimeException("Lỗi lưu trữ outbox event: " + e.getMessage(), e);
        }
    }

    private String resolveEmail(String email, String fallback) {
        if (email != null && !email.trim().isEmpty() && email.contains("@")) {
            return email.trim().toLowerCase();
        }
        return fallback.trim().toLowerCase();
    }
}
