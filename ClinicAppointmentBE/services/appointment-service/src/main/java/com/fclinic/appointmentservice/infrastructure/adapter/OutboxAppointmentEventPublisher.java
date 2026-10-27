package com.fclinic.appointmentservice.infrastructure.adapter;

import static com.fclinic.appointmentservice.infrastructure.AppointmentJson.*;

import com.fclinic.appointmentservice.application.port.out.AppointmentEventPublisherPort;
import com.fclinic.appointmentservice.domain.event.*;
import com.fclinic.appointmentservice.infrastructure.entity.JpaOutboxEventEntity;
import com.fclinic.appointmentservice.infrastructure.persistence.SpringDataJpaOutboxEventRepository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class OutboxAppointmentEventPublisher implements AppointmentEventPublisherPort {
  private final SpringDataJpaOutboxEventRepository repository;
  private final JdbcTemplate jdbc;

  public OutboxAppointmentEventPublisher(
      SpringDataJpaOutboxEventRepository repository, JdbcTemplate jdbc) {
    this.repository = repository;
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void publish(AppointmentDomainEvent event) {
    var a = event.snapshot();
    Map<String, Object> n = new LinkedHashMap<>();
    n.put("eventId", event.eventId().toString());
    n.put("eventType", event.eventType());
    n.put("schemaVersion", 2);
    n.put("occurredAt", event.occurredAt().toString());
    n.put("source", "appointment-service");
    n.put("appointmentId", a.id());
    n.put("aggregateVersion", a.version());
    n.put("bookingCode", a.bookingCode());
    n.put("patientId", a.patientId().toString());
    n.put("patientName", a.patientName());
    n.put("patientPhone", a.patientPhone());
    n.put("doctorId", a.doctorId());
    n.put("doctorName", a.doctorName());
    n.put("doctorSpecialty", a.doctorSpecialty());
    n.put("slotId", a.slotId());
    n.put("appointmentDate", a.appointmentDate().toString());
    n.put("startTime", time(a.startTime()));
    n.put("endTime", time(a.endTime()));
    n.put("timezone", "Asia/Saigon");
    if (event instanceof AppointmentRescheduledDomainEvent r) {
      n.put("previousSlotId", r.previousSlotId());
      n.put("previousAppointmentDate", r.previousAppointmentDate().toString());
      n.put("previousStartTime", time(r.previousStartTime()));
      n.put("previousEndTime", time(r.previousEndTime()));
    }
    if (event instanceof AppointmentReminderDomainEvent r)
      n.put("reminderDueAt", r.reminderDueAt().toString());
    repository.saveAndFlush(
        JpaOutboxEventEntity.pending(
            event.eventId(), a.id(), a.version(), event.eventType(), write(n), event.occurredAt()));
    if (event instanceof AppointmentReminderDomainEvent r)
      jdbc.update(
          "INSERT INTO"
              + " reminder_publications(appointment_id,aggregate_version,reminder_due_at,event_id)"
              + " VALUES(?,?,?,?)",
          a.id(),
          a.version(),
          Timestamp.from(r.reminderDueAt()),
          event.eventId());
  }

  private String time(LocalTime value) {
    return value.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
  }
}
