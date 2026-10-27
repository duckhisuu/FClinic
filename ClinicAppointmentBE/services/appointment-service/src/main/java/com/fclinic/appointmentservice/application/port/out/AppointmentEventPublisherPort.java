package com.fclinic.appointmentservice.application.port.out;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.event.AppointmentDomainEvent;
import java.time.*;
import java.util.*;

public interface AppointmentEventPublisherPort {
  void publish(AppointmentDomainEvent event);
}
