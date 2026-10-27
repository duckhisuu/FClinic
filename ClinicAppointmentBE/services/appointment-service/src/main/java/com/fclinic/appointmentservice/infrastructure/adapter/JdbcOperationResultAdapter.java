package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fclinic.appointmentservice.application.port.out.OperationResultPort;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.infrastructure.AppointmentJson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcOperationResultAdapter implements OperationResultPort {
  private final JdbcTemplate jdbc;

  public JdbcOperationResultAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<StoredOperationResult> find(String subject, String operation, UUID key) {
    return jdbc
        .query(
            "SELECT request_hash,response_status,response_body FROM operation_results WHERE"
                + " caller_subject=? AND operation=? AND idempotency_key=?",
            (r, i) ->
                new StoredOperationResult(
                    r.getString(1), new AppointmentOperationResult(r.getInt(2), r.getString(3))),
            subject,
            operation,
            key)
        .stream()
        .findFirst();
  }

  public void save(
      OperationAttempt a, long appointmentId, AppointmentOperationResult result, Instant now) {
    jdbc.update(
        "INSERT INTO"
            + " operation_results(caller_subject,operation,idempotency_key,request_hash,appointment_id,response_status,response_body,created_at)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        a.subject(),
        a.operation(),
        a.key(),
        a.requestHash(),
        appointmentId,
        result.status(),
        result.body(),
        Timestamp.from(now));
  }

  public String fingerprint(String resource, Map<String, Object> canonicalBody) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      ("v1\n"
                              + resource
                              + "\n"
                              + AppointmentJson.write(new TreeMap<>(canonicalBody)))
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
