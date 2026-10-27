package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fclinic.appointmentservice.domain.event.AppointmentBookedDomainEvent;
import com.fclinic.appointmentservice.domain.event.AppointmentCancelledDomainEvent;
import com.fclinic.appointmentservice.infrastructure.entity.JpaOutboxEventEntity;
import com.fclinic.appointmentservice.infrastructure.persistence.SpringDataJpaOutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OutboxAppointmentEventPublisherTest {

    private SpringDataJpaOutboxEventRepository outboxRepository;
    private ObjectMapper objectMapper;
    private OutboxAppointmentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(SpringDataJpaOutboxEventRepository.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        publisher = new OutboxAppointmentEventPublisher(outboxRepository, objectMapper);
    }

    @Test
    @DisplayName("Should serialize AppointmentBookedDomainEvent into nested APPOINTMENT_CREATED notification payload")
    void shouldSerializeBookedEventToNotificationPayload() throws Exception {
        AppointmentBookedDomainEvent event = AppointmentBookedDomainEvent.from(
                101L,
                "FC-BOOK001",
                15L,
                "Nguyễn Văn Hùng",
                "0912345678",
                "hung.nguyen@example.com",
                2L,
                "BS. CKII Nguyễn Văn An",
                "Tim Mạch",
                "doctor2@fclinic.vn",
                5L,
                LocalDate.of(2026, 10, 28),
                LocalTime.of(8, 0, 0),
                LocalTime.of(8, 30, 0),
                "Khám tim mạch",
                350000.0
        );

        publisher.publish(event);

        ArgumentCaptor<JpaOutboxEventEntity> captor = ArgumentCaptor.forClass(JpaOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());

        JpaOutboxEventEntity saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("APPOINTMENT_CREATED");
        assertThat(saved.getAggregateType()).isEqualTo("APPOINTMENT");
        assertThat(saved.getAggregateId()).isEqualTo(event.eventId().toString());

        JsonNode root = objectMapper.readTree(saved.getPayload());
        assertThat(root.get("eventType").asText()).isEqualTo("APPOINTMENT_CREATED");
        assertThat(root.get("source").asText()).isEqualTo("appointment-service");

        // Validate appointment snapshot
        JsonNode appt = root.get("appointment");
        assertThat(appt.get("appointmentId").asLong()).isEqualTo(101L);
        assertThat(appt.get("appointmentDate").asText()).isEqualTo("2026-10-28");
        assertThat(appt.get("startTime").asText()).isEqualTo("08:00:00");
        assertThat(appt.get("clinicName").asText()).isEqualTo("FClinic");
        assertThat(appt.get("reason").asText()).isEqualTo("Khám tim mạch");

        // Validate patient recipient
        JsonNode patient = root.get("patient");
        assertThat(patient.get("userId").asLong()).isEqualTo(15L);
        assertThat(patient.get("recipientType").asText()).isEqualTo("PATIENT");
        assertThat(patient.get("displayName").asText()).isEqualTo("Nguyễn Văn Hùng");
        assertThat(patient.get("email").asText()).isEqualTo("hung.nguyen@example.com");

        // Validate doctor recipient
        JsonNode doctor = root.get("doctor");
        assertThat(doctor.get("userId").asLong()).isEqualTo(2L);
        assertThat(doctor.get("recipientType").asText()).isEqualTo("DOCTOR");
        assertThat(doctor.get("displayName").asText()).isEqualTo("BS. CKII Nguyễn Văn An");
        assertThat(doctor.get("email").asText()).isEqualTo("doctor2@fclinic.vn");
    }

    @Test
    @DisplayName("Should use safe fallback email addresses when email is absent")
    void shouldGenerateFallbackEmailsWhenMissing() throws Exception {
        AppointmentBookedDomainEvent event = AppointmentBookedDomainEvent.from(
                102L,
                "FC-BOOK002",
                16L,
                "Lê Thuỳ Linh",
                "0987654321",
                3L,
                "ThS. BS Trần Thị Mai",
                "Nhi Khoa",
                6L,
                LocalDate.of(2026, 10, 29),
                LocalTime.of(14, 0, 0),
                LocalTime.of(14, 30, 0),
                "Khám nhi",
                300000.0
        );

        publisher.publish(event);

        ArgumentCaptor<JpaOutboxEventEntity> captor = ArgumentCaptor.forClass(JpaOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());

        JsonNode root = objectMapper.readTree(captor.getValue().getPayload());
        assertThat(root.get("patient").get("email").asText()).isEqualTo("0987654321@fclinic.local");
        assertThat(root.get("doctor").get("email").asText()).isEqualTo("doctor3@fclinic.vn");
    }

    @Test
    @DisplayName("Should serialize AppointmentCancelledDomainEvent with cancellation reason")
    void shouldSerializeCancelledEvent() throws Exception {
        AppointmentCancelledDomainEvent event = AppointmentCancelledDomainEvent.from(
                101L,
                "FC-BOOK001",
                15L,
                "Nguyễn Văn Hùng",
                "0912345678",
                "hung.nguyen@example.com",
                2L,
                "BS. CKII Nguyễn Văn An",
                "Tim Mạch",
                "doctor2@fclinic.vn",
                LocalDate.of(2026, 10, 28),
                LocalTime.of(8, 0, 0),
                LocalTime.of(8, 30, 0),
                "Bận công tác đột xuất"
        );

        publisher.publish(event);

        ArgumentCaptor<JpaOutboxEventEntity> captor = ArgumentCaptor.forClass(JpaOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());

        JpaOutboxEventEntity saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("APPOINTMENT_CANCELLED");

        JsonNode root = objectMapper.readTree(saved.getPayload());
        assertThat(root.get("eventType").asText()).isEqualTo("APPOINTMENT_CANCELLED");
        assertThat(root.get("appointment").get("cancellationReason").asText()).isEqualTo("Bận công tác đột xuất");
    }
}
