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
public class GetAppointmentHistoryService implements GetAppointmentHistoryUseCase {
  private final AppointmentRepositoryPort repository;
  private final AppointmentHistoryPort history;
  private final AppointmentPolicy policy;

  public GetAppointmentHistoryService(
      AppointmentRepositoryPort repository, AppointmentHistoryPort history, AppointmentPolicy policy) {
    this.repository = repository;
    this.history = history;
    this.policy = policy;
  }

  public List<AppointmentHistoryView> getHistory(AppointmentActor actor, UUID id) {
    policy.authenticated(actor);
    policy.read(actor, repository.findById(id).orElseThrow(AppointmentNotFoundException::new));
    return history.history(id);
  }
}
