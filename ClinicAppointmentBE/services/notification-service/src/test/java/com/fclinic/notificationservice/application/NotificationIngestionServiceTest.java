package com.fclinic.notificationservice.application;

import com.fclinic.notificationservice.application.dto.event.AppointmentNotificationEvent;
import com.fclinic.notificationservice.application.dto.event.AppointmentSnapshotDto;
import com.fclinic.notificationservice.application.dto.PreviousAppointmentSlotDto;
import com.fclinic.notificationservice.application.dto.RecipientDto;
import com.fclinic.notificationservice.application.exception.InvalidNotificationEventException;
import com.fclinic.notificationservice.application.port.out.NotificationRepositoryPort;
import com.fclinic.notificationservice.application.port.out.ProcessedEventPort;
import com.fclinic.notificationservice.application.port.out.TemplateRenderPort;
import com.fclinic.notificationservice.application.usecase.NotificationIngestionService;
import com.fclinic.notificationservice.domain.aggregate.Notification;
import com.fclinic.notificationservice.domain.model.AppointmentEventType;
import com.fclinic.notificationservice.domain.model.NotificationType;
import com.fclinic.notificationservice.domain.model.RecipientType;
import com.fclinic.notificationservice.domain.valueobject.RenderedMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationIngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");

    private ProcessedEventPort processedEvents;
    private NotificationRepositoryPort notifications;
    private TemplateRenderPort templates;
    private NotificationIngestionService service;

    @BeforeEach
    void setUp() {
        processedEvents = mock(ProcessedEventPort.class);
        notifications = mock(NotificationRepositoryPort.class);
        templates = mock(TemplateRenderPort.class);
        when(templates.render(any(), any(), any())).thenReturn(new RenderedMessage("subject", "<p>body</p>"));
        when(notifications.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new NotificationIngestionService(processedEvents, notifications, templates,
                Validation.buildDefaultValidatorFactory().getValidator(), Clock.fixed(NOW, ZoneOffset.UTC),
                new SimpleMeterRegistry(), new NoopTransactionManager());
    }

    private AppointmentNotificationEvent event(AppointmentEventType type, PreviousAppointmentSlotDto previous) {
        return new AppointmentNotificationEvent(UUID.randomUUID(), type, NOW, "appointment-service",
                new AppointmentSnapshotDto(152L, LocalDate.of(2026, 10, 20), LocalTime.of(10, 0), LocalTime.of(10, 30),
                        "FClinic", "Room 203", "Check-up", null),
                new RecipientDto(25L, RecipientType.PATIENT, "Nguyen Van A", "Patient@Example.com"),
                new RecipientDto(7L, RecipientType.DOCTOR, "Dr. Tran", "doctor@example.com"), previous);
    }

    @Test
    void createdEventProducesPatientAndDoctorNotifications() {
        service.ingest(event(AppointmentEventType.APPOINTMENT_CREATED, null));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getRecipientType)
                .containsExactly(RecipientType.PATIENT, RecipientType.DOCTOR);
        assertThat(saved.getAllValues()).extracting(Notification::getType)
                .containsOnly(NotificationType.APPOINTMENT_CONFIRMATION);
        assertThat(saved.getAllValues().get(0).getRecipientEmail()).isEqualTo("patient@example.com");
        verify(processedEvents).save(any(), any());
    }

    @Test
    void eventTypeMapsToNotificationType() {
        service.ingest(event(AppointmentEventType.APPOINTMENT_CANCELLED, null));
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getType)
                .containsOnly(NotificationType.APPOINTMENT_CANCELLATION);
    }

    @Test
    void duplicateEventCreatesNothing() {
        AppointmentNotificationEvent e = event(AppointmentEventType.APPOINTMENT_CREATED, null);
        when(processedEvents.exists(e.eventId())).thenReturn(true);

        service.ingest(e);

        verify(notifications, never()).save(any());
        verify(processedEvents, never()).save(any(), any());
    }

    @Test
    void concurrentDuplicateOnProcessedEventKeyIsTreatedAsProcessed() {
        AppointmentNotificationEvent e = event(AppointmentEventType.APPOINTMENT_CREATED, null);
        when(processedEvents.exists(e.eventId())).thenReturn(false, true);
        doThrow(new DataIntegrityViolationException("pk")).when(processedEvents).save(any(), any());

        service.ingest(e);

        verify(notifications, never()).save(any());
    }

    @Test
    void unrelatedIntegrityViolationIsRethrown() {
        AppointmentNotificationEvent e = event(AppointmentEventType.APPOINTMENT_CREATED, null);
        when(processedEvents.exists(e.eventId())).thenReturn(false);
        doThrow(new DataIntegrityViolationException("other")).when(processedEvents).save(any(), any());

        assertThatThrownBy(() -> service.ingest(e)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void malformedEventIsRejected() {
        AppointmentNotificationEvent bad = new AppointmentNotificationEvent(null, AppointmentEventType.APPOINTMENT_CREATED,
                NOW, "x", null, null, null, null);
        assertThatThrownBy(() -> service.ingest(bad)).isInstanceOf(InvalidNotificationEventException.class);
        verify(notifications, never()).save(any());
    }

    @Test
    void rescheduleRequiresPreviousSlot() {
        assertThatThrownBy(() -> service.ingest(event(AppointmentEventType.APPOINTMENT_RESCHEDULED, null)))
                .isInstanceOf(InvalidNotificationEventException.class)
                .hasMessageContaining("previousSlot");

        service.ingest(event(AppointmentEventType.APPOINTMENT_RESCHEDULED,
                new PreviousAppointmentSlotDto(LocalDate.of(2026, 10, 18), LocalTime.of(9, 0))));
        verify(notifications, times(2)).save(any());
    }

    /** Transactions are not under test here; the real boundary is exercised by the end-to-end run. */
    private static class NoopTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
