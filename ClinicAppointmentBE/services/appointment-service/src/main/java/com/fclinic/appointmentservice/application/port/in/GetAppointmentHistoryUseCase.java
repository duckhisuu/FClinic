package com.fclinic.appointmentservice.application.port.in;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.application.AppointmentActor;
import java.time.*;
import java.util.*;

public interface GetAppointmentHistoryUseCase {
  List<AppointmentHistoryView> getHistory(AppointmentActor actor, UUID id);
}
