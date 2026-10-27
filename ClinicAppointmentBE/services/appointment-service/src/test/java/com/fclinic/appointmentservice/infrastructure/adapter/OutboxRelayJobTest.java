package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fclinic.appointmentservice.infrastructure.entity.JpaOutboxEventEntity;
import com.fclinic.appointmentservice.infrastructure.persistence.SpringDataJpaOutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxRelayJobTest {

    private SpringDataJpaOutboxEventRepository repository;
    private RabbitTemplate rabbitTemplate;
    private OutboxRelayJob relayJob;

    @BeforeEach
    void setUp() {
        repository = mock(SpringDataJpaOutboxEventRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        relayJob = new OutboxRelayJob(repository, rabbitTemplate, "clinic.events", "appointment.created", 50);
    }

    @Test
    @DisplayName("Should determine exact routing keys matching notification-service bindings")
    void shouldDetermineCorrectRoutingKeys() {
        assertThat(relayJob.determineRoutingKey("APPOINTMENT_CREATED")).isEqualTo("appointment.created");
        assertThat(relayJob.determineRoutingKey("APPOINTMENT_BOOKED")).isEqualTo("appointment.created");
        assertThat(relayJob.determineRoutingKey("APPOINTMENT_CANCELLED")).isEqualTo("appointment.cancelled");
        assertThat(relayJob.determineRoutingKey("APPOINTMENT_RESCHEDULED")).isEqualTo("appointment.rescheduled");
        assertThat(relayJob.determineRoutingKey("APPOINTMENT_REMINDER")).isEqualTo("appointment.reminder");
    }

    @Test
    @DisplayName("Should publish pending outbox events to clinic.events and mark completed")
    void shouldPublishPendingOutboxEvents() {
        UUID eventId = UUID.randomUUID();
        JpaOutboxEventEntity pendingEvent = JpaOutboxEventEntity.pending(
                eventId,
                eventId.toString(),
                "APPOINTMENT",
                "APPOINTMENT_CREATED",
                "{\"eventId\":\"" + eventId + "\"}",
                Instant.now()
        );

        when(repository.findPendingForUpdate(50)).thenReturn(List.of(pendingEvent));

        relayJob.relay();

        verify(rabbitTemplate).convertAndSend(
                eq("clinic.events"),
                eq("appointment.created"),
                eq("{\"eventId\":\"" + eventId + "\"}"),
                any(MessagePostProcessor.class)
        );

        ArgumentCaptor<JpaOutboxEventEntity> captor = ArgumentCaptor.forClass(JpaOutboxEventEntity.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
    }
}
