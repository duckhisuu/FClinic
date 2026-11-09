package com.fclinic.appointmentservice.application.port.in;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.application.AppointmentActor;
import java.time.*;
import java.util.*;

public interface GetAppointmentUseCase {
  AppointmentView getById(AppointmentActor actor, UUID id);
}
