package com.fclinic.notificationservice.application.usecase;

import com.fclinic.notificationservice.application.dto.event.AppointmentNotificationEvent;
import com.fclinic.notificationservice.application.dto.RecipientDto;
import com.fclinic.notificationservice.application.exception.InvalidNotificationEventException;
import com.fclinic.notificationservice.application.port.in.IngestAppointmentEventUseCase;
import com.fclinic.notificationservice.application.port.out.NotificationRepositoryPort;
import com.fclinic.notificationservice.application.port.out.ProcessedEventPort;
import com.fclinic.notificationservice.application.port.out.TemplateRenderPort;
import com.fclinic.notificationservice.domain.aggregate.Notification;
import com.fclinic.notificationservice.domain.model.AppointmentEventType;
import com.fclinic.notificationservice.domain.model.NotificationType;
import com.fclinic.notificationservice.domain.valueobject.RenderedMessage;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Phase A: turns an event into durable jobs. Never touches SMTP. The processed-event marker and the
 * notification rows commit in one transaction.
 */
@Slf4j
@Service
public class NotificationIngestionService implements IngestAppointmentEventUseCase {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ProcessedEventPort processedEventPort;
    private final NotificationRepositoryPort notificationRepository;
    private final TemplateRenderPort templateRenderPort;
    private final Validator validator;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate transactionTemplate;

    public NotificationIngestionService(ProcessedEventPort processedEventPort,
                                        NotificationRepositoryPort notificationRepository,
                                        TemplateRenderPort templateRenderPort,
                                        Validator validator,
                                        Clock clock,
                                        MeterRegistry meterRegistry,
                                        PlatformTransactionManager transactionManager) {
        this.processedEventPort = processedEventPort;
        this.notificationRepository = notificationRepository;
        this.templateRenderPort = templateRenderPort;
        this.validator = validator;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void ingest(AppointmentNotificationEvent event) {
        validate(event);

        try {
            Boolean created = transactionTemplate.execute(status -> doIngest(event));
            if (Boolean.TRUE.equals(created)) {
                meterRegistry.counter("notification.events.ingested").increment();
                var appt = event.appointment();
                log.info("event_ingested eventId={} appointmentId={}", event.eventId(),
                        appt != null ? appt.appointmentId() : null);
            } else {
                duplicate(event);
            }
        } catch (DataIntegrityViolationException ex) {
            // A concurrent consumer may have committed the same eventId first. Only that case is benign.
            if (processedEventPort.exists(event.eventId())) {
                duplicate(event);
                return;
            }
            throw ex;
        }
    }

    private void duplicate(AppointmentNotificationEvent event) {
        meterRegistry.counter("notification.events.duplicate").increment();
        log.warn("duplicate_event_ignored eventId={}", event.eventId());
    }

    private boolean doIngest(AppointmentNotificationEvent event) {
        if (processedEventPort.exists(event.eventId())) {
            return false;
        }
        Instant now = clock.instant();
        NotificationType type = mapType(event.eventType());

        // Insert the marker first so a duplicate race fails here, before any notification rows are written.
        processedEventPort.save(event, now);
        RecipientDto patient = event.patient();
        if (patient != null && patient.email() != null && !patient.email().isBlank()) {
            createNotification(event, patient, type, now);
        } else if (patient != null) {
            log.info("Skipping email creation for patient without email: eventId={}", event.eventId());
        }
        RecipientDto doctor = event.doctor();
        if (doctor != null && doctor.email() != null && !doctor.email().isBlank()) {
            createNotification(event, doctor, type, now);
        } else if (doctor != null) {
            log.info("Skipping email creation for doctor without email: eventId={}", event.eventId());
        }
        return true;
    }

    private void createNotification(AppointmentNotificationEvent event, RecipientDto recipient,
                                    NotificationType type, Instant now) {
        var appt = event.appointment();
        RenderedMessage rendered = templateRenderPort.render(type, recipient.recipientType(),
                buildVariables(event, recipient));

        Notification saved = notificationRepository.save(Notification.pending(
                event.eventId(),
                appt != null ? appt.appointmentId() : 0L,
                recipient.userId(),
                recipient.recipientType(),
                recipient.displayName(),
                recipient.email().trim().toLowerCase(),
                type,
                rendered.subject(),
                rendered.htmlBody(),
                now));

        log.info("notification_created notificationId={} eventId={} appointmentId={} recipientUserId={} notificationType={}",
                saved.getId(), event.eventId(), saved.getAppointmentId(), saved.getRecipientUserId(), type);
    }

    private void validate(AppointmentNotificationEvent event) {
        if (event == null) {
            throw new InvalidNotificationEventException("Event payload is null");
        }
        Set<ConstraintViolation<AppointmentNotificationEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            log.error("invalid_event eventId={} violations={}", event.eventId(), detail);
            throw new InvalidNotificationEventException("Invalid event: " + detail);
        }
        var patient = event.patient();
        var doctor = event.doctor();
        if (patient == null || patient.recipientType() != com.fclinic.notificationservice.domain.model.RecipientType.PATIENT
                || doctor == null || doctor.recipientType() != com.fclinic.notificationservice.domain.model.RecipientType.DOCTOR) {
            throw new InvalidNotificationEventException("patient/doctor recipientType does not match its role");
        }
        if (event.appointment() == null) {
            throw new InvalidNotificationEventException("appointment details are missing");
        }
        if (event.eventType() == AppointmentEventType.APPOINTMENT_RESCHEDULED && event.previousSlot() == null) {
            throw new InvalidNotificationEventException("previousSlot is required for APPOINTMENT_RESCHEDULED");
        }
    }

    static NotificationType mapType(AppointmentEventType eventType) {
        return switch (eventType) {
            case APPOINTMENT_CREATED -> NotificationType.APPOINTMENT_CONFIRMATION;
            case APPOINTMENT_CANCELLED -> NotificationType.APPOINTMENT_CANCELLATION;
            case APPOINTMENT_RESCHEDULED -> NotificationType.APPOINTMENT_RESCHEDULED;
            case APPOINTMENT_REMINDER -> NotificationType.APPOINTMENT_REMINDER;
        };
    }

    private Map<String, Object> buildVariables(AppointmentNotificationEvent event, RecipientDto recipient) {
        var appt = event.appointment();
        var patient = event.patient();
        var doctor = event.doctor();
        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName", recipient.displayName());
        vars.put("patientName", patient != null ? patient.displayName() : "");
        vars.put("doctorName", doctor != null ? doctor.displayName() : "");
        vars.put("appointmentId", appt != null ? appt.appointmentId() : "");
        vars.put("appointmentDate", appt != null && appt.appointmentDate() != null ? appt.appointmentDate().format(DATE_FORMAT) : "");
        vars.put("appointmentTime", appt != null && appt.startTime() != null ? appt.startTime().format(TIME_FORMAT) : "");
        vars.put("appointmentEndTime", appt != null && appt.endTime() != null ? appt.endTime().format(TIME_FORMAT) : "");
        vars.put("clinicName", appt != null && appt.clinicName() != null ? appt.clinicName() : "FClinic");
        vars.put("roomName", appt != null && appt.roomName() != null ? appt.roomName() : "");
        vars.put("reason", appt != null && appt.reason() != null ? appt.reason() : "");
        vars.put("cancellationReason", appt != null && appt.cancellationReason() != null ? appt.cancellationReason() : "");
        var prev = event.previousSlot();
        if (prev != null) {
            vars.put("previousAppointmentDate", prev.appointmentDate() != null ? prev.appointmentDate().format(DATE_FORMAT) : "");
            vars.put("previousAppointmentTime", prev.startTime() != null ? prev.startTime().format(TIME_FORMAT) : "");
        }
        return vars;
    }
}
