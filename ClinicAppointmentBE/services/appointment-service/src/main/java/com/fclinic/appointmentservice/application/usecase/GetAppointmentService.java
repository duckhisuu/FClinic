package com.fclinic.appointmentservice.application.usecase;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.exception.*;
import com.fclinic.appointmentservice.application.port.in.*;
import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.event.*;
import com.fclinic.appointmentservice.application.AppointmentActor;
import com.fclinic.appointmentservice.application.security.AppointmentPolicy;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GetAppointmentService implements GetAppointmentUseCase {
  private final AppointmentRepositoryPort repository;
  private final AppointmentPolicy policy;

  public GetAppointmentService(AppointmentRepositoryPort repository, AppointmentPolicy policy) {
    this.repository = repository;
    this.policy = policy;
  }

  public AppointmentView getById(AppointmentActor actor, UUID id) {
    policy.authenticated(actor);
    var appointment = repository.findById(id).orElseThrow(AppointmentNotFoundException::new);
    policy.read(actor, appointment);
    return AppointmentView.from(appointment);
  }
}
