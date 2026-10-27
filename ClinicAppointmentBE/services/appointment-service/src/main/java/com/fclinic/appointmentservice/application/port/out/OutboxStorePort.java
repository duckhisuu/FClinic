package com.fclinic.appointmentservice.application.port.out;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import java.time.*;
import java.util.*;

public interface OutboxStorePort {
  List<OutboxMessage> claim(Instant now);

  void ready(OutboxMessage row, String payload, Instant now);

  void published(OutboxMessage row, Instant now);

  void retry(OutboxMessage row, Instant now, String code);

  void suppress(OutboxMessage row, Instant now);

  boolean reminderExists(Appointment appointment, Instant due);
}
