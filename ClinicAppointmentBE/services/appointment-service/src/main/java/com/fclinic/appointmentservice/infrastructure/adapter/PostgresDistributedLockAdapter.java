package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fclinic.appointmentservice.application.port.out.DistributedLockPort;
import com.fclinic.appointmentservice.domain.AppointmentError;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Immediate session guard for an idempotency key, independent of the JPA write connection. */
@Component
@ConditionalOnProperty(
    name = "appointment.lock-provider",
    havingValue = "postgres",
    matchIfMissing = true)
public class PostgresDistributedLockAdapter implements DistributedLockPort {
  private final String url;
  private final Properties properties = new Properties();
  private final Semaphore permits = new Semaphore(16);

  public PostgresDistributedLockAdapter(
      @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String user,
      @Value("${spring.datasource.password}") String password) {
    this.url = url;
    properties.setProperty("user", user);
    properties.setProperty("password", password);
    properties.setProperty("connectTimeout", "3");
    properties.setProperty("socketTimeout", "5");
  }

  public <T> T executeWithLock(String key, Supplier<T> task) {
    if (!permits.tryAcquire()) throw AppointmentError.unavailable();
    try (Connection connection = DriverManager.getConnection(url, properties)) {
      long guard;
      try {
        guard =
            ByteBuffer.wrap(
                    MessageDigest.getInstance("SHA-256")
                        .digest(key.getBytes(StandardCharsets.UTF_8)))
                .getLong();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      try (PreparedStatement s = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
        s.setLong(1, guard);
        try (ResultSet r = s.executeQuery()) {
          r.next();
          if (!r.getBoolean(1))
            throw AppointmentError.conflict(
                "OPERATION_IN_PROGRESS", "Yêu cầu đang được xử lý. Hãy thử lại với cùng khóa.");
        }
      }
      try {
        return task.get();
      } finally {
        try (PreparedStatement s = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
          s.setLong(1, guard);
          s.execute();
        }
      }
    } catch (SQLException e) {
      throw AppointmentError.unavailable();
    } finally {
      permits.release();
    }
  }
}
