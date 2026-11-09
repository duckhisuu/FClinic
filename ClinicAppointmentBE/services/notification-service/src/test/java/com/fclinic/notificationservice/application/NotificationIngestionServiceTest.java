package com.fclinic.notificationservice.application;

import com.fclinic.notificationservice.application.dto.event.AppointmentNotificationEvent;
import com.fclinic.notificationservice.application.dto.AppointmentSnapshotDto;
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
    private static final UUID APPOINTMENT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID PATIENT = UUID.fromString("11111111-1111-4111-8111-111111111104");
    private static final UUID DOCTOR = UUID.fromString("11111111-1111-4111-8111-111111111103");

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
                new AppointmentSnapshotDto(APPOINTMENT, LocalDate.of(2026, 10, 20), LocalTime.of(10, 0), LocalTime.of(10, 30),
                        "FClinic", "Room 203", "Check-up", null),
                new RecipientDto(PATIENT, RecipientType.PATIENT, "Nguyen Van A", "Patient@Example.com"),
                new RecipientDto(DOCTOR, RecipientType.DOCTOR, "Dr. Tran", "doctor@example.com"), previous);
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
        assertThat(saved.getAllValues().get(0).getRecipientUserId()).isEqualTo(PATIENT);
        assertThat(saved.getAllValues().get(1).getRecipientUserId()).isEqualTo(DOCTOR);
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
    void absentDoctorAccountIsRejectedRatherThanGuessed() {
        var original = event(AppointmentEventType.APPOINTMENT_CREATED, null);
        var missing = new AppointmentNotificationEvent(original.eventId(), original.eventType(), NOW,
                original.source(), original.appointment(), original.patient(),
                new RecipientDto(null, RecipientType.DOCTOR, "Bác sĩ", "doctor@example.com"), null);
        assertThatThrownBy(() -> service.ingest(missing)).isInstanceOf(InvalidNotificationEventException.class);
        verify(notifications, never()).save(any());
    }

    @Test
    void unregisteredPatientIsEmailOnlyAndNeverAssignedAnInbox() {
        var original = event(AppointmentEventType.APPOINTMENT_CREATED, null);
        var e = new AppointmentNotificationEvent(original.eventId(), original.eventType(), NOW,
                original.source(), original.appointment(),
                new RecipientDto(null, RecipientType.PATIENT, "Bệnh nhân tại quầy", "walkin@example.test"),
                original.doctor(), null);
        service.ingest(e);
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getRecipientUserId()).isNull();
        assertThat(saved.getAllValues().get(0).getRecipientEmail()).isEqualTo("walkin@example.test");
        assertThat(saved.getAllValues().get(1).getRecipientUserId()).isEqualTo(DOCTOR);
    }

    @Test
    void numericLegacyRecipientCannotDeserializeAsAUuid() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThatThrownBy(() -> mapper.readValue(
                "{\"recipientType\":\"PATIENT\",\"userId\":1,\"name\":\"A\",\"email\":\"a@example.test\"}",
                com.fclinic.notificationservice.application.dto.event.EventRecipientDto.class))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
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

    @Test
    void v3SchemaEventFromAppointmentServiceIsIngestedSuccessfully() {
        AppointmentNotificationEvent v2Event = new AppointmentNotificationEvent(
                UUID.randomUUID(),
                AppointmentEventType.APPOINTMENT_CREATED,
                NOW,
                "appointment-service",
                null, null, null, null,
                3,
                APPOINTMENT,
                0L,
                "FC-DEMO-0101",
                "74fba630-5953-44b8-86d6-bc236ac1213b",
                "Demo Patient",
                "+84900000001",
                UUID.randomUUID(),
                "Demo Doctor",
                "General Medicine",
                UUID.randomUUID(),
                LocalDate.of(2026, 11, 2),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                "Asia/Saigon",
                java.util.List.of(
                        new com.fclinic.notificationservice.application.dto.event.EventRecipientDto("PATIENT", PATIENT, "Demo Patient", "patient@example.test"),
                        new com.fclinic.notificationservice.application.dto.event.EventRecipientDto("DOCTOR", DOCTOR, "Demo Doctor", "doctor@example.test")
                ),
                null, null, null, null, null
        );

        service.ingest(v2Event);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getRecipientType)
                .containsExactly(RecipientType.PATIENT, RecipientType.DOCTOR);
        assertThat(saved.getAllValues().get(0).getRecipientEmail()).isEqualTo("patient@example.test");
        assertThat(saved.getAllValues().get(1).getRecipientEmail()).isEqualTo("doctor@example.test");
        assertThat(saved.getAllValues().get(0).getAppointmentId()).isEqualTo(APPOINTMENT);
    }

    @Test
    void fixtureCreatedJsonDeserializesAndIngests() throws Exception {
        String json = """
        {
          "bookingCode": "FC-DEMO-0101",
          "patientId": "74fba630-5953-44b8-86d6-bc236ac1213b",
          "patientName": "Demo Patient",
          "patientPhone": "+84900000001",
          "doctorId": "33333333-3333-4333-8333-333333333301",
          "doctorName": "Demo Doctor",
          "doctorSpecialty": "General Medicine",
          "slotId": "44444444-4444-4444-8444-444444444444",
          "appointmentDate": "2026-11-02",
          "startTime": "09:00:00",
          "endTime": "10:00:00",
          "eventId": "78357fb9-0d1c-4a4a-9f33-583928367ffc",
          "eventType": "APPOINTMENT_CREATED",
          "schemaVersion": 3,
          "occurredAt": "2026-10-20T03:00:00Z",
          "source": "appointment-service",
          "appointmentId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
          "aggregateVersion": 0,
          "timezone": "Asia/Saigon",
          "recipients": [
            {
              "recipientType": "PATIENT",
              "userId": "11111111-1111-4111-8111-111111111104",
              "name": "Demo Patient",
              "email": "patient@example.test"
            },
            {
              "recipientType": "DOCTOR",
              "userId": "11111111-1111-4111-8111-111111111103",
              "name": "Demo Doctor",
              "email": "doctor@example.test"
            }
          ]
        }
        """;
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        AppointmentNotificationEvent event = mapper.readValue(json, AppointmentNotificationEvent.class);

        assertThat(event.appointment().appointmentId()).isEqualTo(APPOINTMENT);
        assertThat(event.patient().displayName()).isEqualTo("Demo Patient");
        assertThat(event.doctor().displayName()).isEqualTo("Demo Doctor");

        service.ingest(event);
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
