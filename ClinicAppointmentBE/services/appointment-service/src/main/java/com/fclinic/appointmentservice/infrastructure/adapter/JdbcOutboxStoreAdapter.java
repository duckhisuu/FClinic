package com.fclinic.appointmentservice.infrastructure.adapter;

import static com.fclinic.appointmentservice.infrastructure.AppointmentJson.*;

import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcOutboxStoreAdapter implements OutboxStorePort {
  private final JdbcTemplate jdbc;
  private final UnitOfWorkPort work;

  public JdbcOutboxStoreAdapter(JdbcTemplate jdbc, UnitOfWorkPort work) {
    this.jdbc = jdbc;
    this.work = work;
  }

  private <T> T transaction(Supplier<T> task) {
    return work.execute(task);
  }

  private ReminderContext reminderContext(String type, String intent) {
    if (!type.equals("APPOINTMENT_REMINDER")) return null;
    var n = read(intent);
    return new ReminderContext(
        n.path("slotId").longValue(),
        LocalDate.parse(n.path("appointmentDate").asText()),
        LocalTime.parse(n.path("startTime").asText()),
        LocalTime.parse(n.path("endTime").asText()));
  }

  public List<OutboxMessage> claim(Instant now) {
    return transaction(
        () -> {
          UUID token = UUID.randomUUID();
          return jdbc.query(
              "UPDATE outbox_events SET claim_token=?,claimed_until=?,updated_at=? WHERE id IN"
                  + " (SELECT id FROM outbox_events WHERE status IN ('PENDING_ENRICHMENT','READY')"
                  + " AND (next_attempt_at IS NULL OR next_attempt_at<=?) AND (claimed_until IS"
                  + " NULL OR claimed_until<?) ORDER BY created_at,id FOR UPDATE SKIP LOCKED LIMIT"
                  + " 1) RETURNING *",
              (r, i) ->
                  new OutboxMessage(
                      r.getObject("id", UUID.class),
                      r.getString("event_type"),
                      r.getLong("aggregate_id"),
                      r.getLong("aggregate_version"),
                      r.getString("intent"),
                      r.getString("payload"),
                      token,
                      reminderContext(r.getString("event_type"), r.getString("intent"))),
              token,
              Timestamp.from(now.plusSeconds(30)),
              Timestamp.from(now),
              Timestamp.from(now),
              Timestamp.from(now));
        });
  }

  public void ready(OutboxMessage row, String payload, Instant now) {
    int n =
        jdbc.update(
            "UPDATE outbox_events SET payload=?,status='READY',updated_at=? WHERE id=? AND"
                + " claim_token=?",
            payload,
            Timestamp.from(now),
            row.id(),
            row.claimToken());
    if (n != 1) throw AppointmentError.unavailable();
  }

  public void published(OutboxMessage row, Instant now) {
    jdbc.update(
        "UPDATE outbox_events SET"
            + " status='PUBLISHED',published_at=?,updated_at=?,claim_token=NULL,claimed_until=NULL,last_error_code=NULL"
            + " WHERE id=? AND claim_token=?",
        Timestamp.from(now),
        Timestamp.from(now),
        row.id(),
        row.claimToken());
  }

  public void retry(OutboxMessage row, Instant now, String code) {
    jdbc.update(
        "UPDATE outbox_events SET"
            + " attempt_count=attempt_count+1,next_attempt_at=?,updated_at=?,claim_token=NULL,claimed_until=NULL,last_error_code=?"
            + " WHERE id=? AND claim_token=?",
        Timestamp.from(now.plusSeconds(30)),
        Timestamp.from(now),
        code,
        row.id(),
        row.claimToken());
  }

  public void suppress(OutboxMessage row, Instant now) {
    jdbc.update(
        "UPDATE outbox_events SET"
            + " status='SUPPRESSED',last_error_code='STALE_REMINDER',updated_at=?,claim_token=NULL,claimed_until=NULL"
            + " WHERE id=? AND claim_token=?",
        Timestamp.from(now),
        row.id(),
        row.claimToken());
  }

  public boolean reminderExists(Appointment a, Instant due) {
    return jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM reminder_publications WHERE appointment_id=? AND"
            + " aggregate_version=? AND reminder_due_at=?)",
        Boolean.class,
        a.getId(),
        a.getVersion(),
        Timestamp.from(due));
  }
}
