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
public class GetAppointmentService implements GetAppointmentUseCase {
  private final AppointmentRepositoryPort repository;

  public GetAppointmentService(AppointmentRepositoryPort repository) {
    this.repository = repository;
  }

  public AppointmentView getById(long id) {
    return AppointmentView.from(
        repository.findById(id).orElseThrow(AppointmentNotFoundException::new));
  }
}
