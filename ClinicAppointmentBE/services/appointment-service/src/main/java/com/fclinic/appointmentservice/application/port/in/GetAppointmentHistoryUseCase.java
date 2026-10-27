package com.fclinic.appointmentservice.application.port.in;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.result.*;
import java.time.*;
import java.util.*;

public interface GetAppointmentHistoryUseCase {
  List<AppointmentHistoryView> getHistory(long id);
}
