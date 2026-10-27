package com.fclinic.appointmentservice.application.usecase;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.exception.*;
import com.fclinic.appointmentservice.application.port.in.*;
import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.event.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GetAppointmentHistoryService implements GetAppointmentHistoryUseCase {
  private final AppointmentRepositoryPort repository;
  private final AppointmentHistoryPort history;

  public GetAppointmentHistoryService(
      AppointmentRepositoryPort repository, AppointmentHistoryPort history) {
    this.repository = repository;
    this.history = history;
  }

  public List<AppointmentHistoryView> getHistory(long id) {
    repository.findById(id).orElseThrow(AppointmentNotFoundException::new);
    return history.history(id);
  }
}
