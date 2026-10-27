package com.fclinic.appointmentservice.application.port.out;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import java.time.*;
import java.util.*;

public interface AppointmentHistoryPort {
  void record(
      Appointment current,
      Appointment previous,
      String action,
      String reason,
      String subject,
      String role,
      Instant now);

  List<AppointmentHistoryView> history(long id);
}
