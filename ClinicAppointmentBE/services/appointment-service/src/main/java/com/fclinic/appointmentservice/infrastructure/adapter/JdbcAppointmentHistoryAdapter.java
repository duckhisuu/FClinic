package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fclinic.appointmentservice.application.port.out.AppointmentHistoryPort;
import com.fclinic.appointmentservice.application.result.AppointmentHistoryView;
import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcAppointmentHistoryAdapter implements AppointmentHistoryPort {
  private final JdbcTemplate jdbc;

  public JdbcAppointmentHistoryAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void record(
      Appointment a,
      Appointment old,
      String action,
      String reason,
      String subject,
      String role,
      Instant now) {
    jdbc.update(
        "INSERT INTO"
            + " appointment_history(appointment_id,action,old_date,old_start_time,new_date,new_start_time,change_reason,actor_subject,changed_by,entity_version,created_at)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        a.getId(),
        action,
        old == null ? null : old.getAppointmentDate(),
        old == null ? null : old.getStartTime(),
        a.getAppointmentDate(),
        a.getStartTime(),
        reason,
        subject,
        role,
        a.getVersion(),
        Timestamp.from(now));
  }

  public List<AppointmentHistoryView> history(long id) {
    return jdbc.query(
        "SELECT * FROM appointment_history WHERE appointment_id=? ORDER BY entity_version,id",
        (r, i) ->
            new AppointmentHistoryView(
                r.getLong("id"),
                r.getString("action"),
                r.getLong("entity_version"),
                r.getObject("old_date", LocalDate.class),
                r.getObject("old_start_time", LocalTime.class),
                r.getObject("new_date", LocalDate.class),
                r.getObject("new_start_time", LocalTime.class),
                r.getString("change_reason"),
                r.getString("changed_by"),
                r.getTimestamp("created_at").toInstant()),
        id);
  }
}
