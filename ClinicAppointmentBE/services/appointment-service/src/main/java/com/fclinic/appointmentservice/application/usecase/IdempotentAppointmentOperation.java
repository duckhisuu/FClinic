package com.fclinic.appointmentservice.application.usecase;

import com.fclinic.appointmentservice.application.AppointmentActor;
import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.AppointmentError;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Shared durable operation boundary; each use case owns its own business transaction. */
@Component
public class IdempotentAppointmentOperation {
  private final OperationResultPort results;
  private final DistributedLockPort locks;
  private final OperationResultCodecPort codec;

  public IdempotentAppointmentOperation(
      OperationResultPort results, DistributedLockPort locks, OperationResultCodecPort codec) {
    this.results = results;
    this.locks = locks;
    this.codec = codec;
  }

  public AppointmentOperationResult execute(
      AppointmentActor actor,
      UUID key,
      String operation,
      String resource,
      Map<String, Object> body,
      Function<OperationAttempt, AppointmentOperationResult> work) {
    String hash = results.fingerprint(resource, body);
    return locks.executeWithLock(
        actor.subject() + "\n" + operation + "\n" + key,
        () -> {
          var stored = results.find(actor.subject(), operation, key);
          if (stored.isPresent()) {
            if (!hash.equals(stored.get().requestHash()))
              throw AppointmentError.conflict(
                  "IDEMPOTENCY_CONFLICT", "Khóa yêu cầu đã được dùng cho nội dung khác.");
            return stored.get().result();
          }
          return work.apply(new OperationAttempt(actor.subject(), operation, key, hash));
        });
  }

  public AppointmentOperationResult complete(
      OperationAttempt attempt, AppointmentView view, int status, Instant now) {
    var response = codec.encode(view, status);
    results.save(attempt, view.id(), response, now);
    return response;
  }

  public static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
