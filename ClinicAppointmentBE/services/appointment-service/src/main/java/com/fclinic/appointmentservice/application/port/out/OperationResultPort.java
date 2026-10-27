package com.fclinic.appointmentservice.application.port.out;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.result.*;
import java.time.*;
import java.util.*;

public interface OperationResultPort {
  Optional<StoredOperationResult> find(String subject, String operation, UUID key);

  void save(
      OperationAttempt attempt, long appointmentId, AppointmentOperationResult result, Instant now);

  String fingerprint(String resource, Map<String, Object> canonicalBody);
}
