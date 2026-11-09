package com.fclinic.appointmentservice.application.usecase;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.exception.*;
import com.fclinic.appointmentservice.application.port.in.*;
import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.domain.event.*;
import com.fclinic.appointmentservice.domain.model.AppointmentStatus;
import com.fclinic.appointmentservice.application.AppointmentActor;
import com.fclinic.appointmentservice.application.security.AppointmentPolicy;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ListAppointmentsService implements ListAppointmentsUseCase {
  private final AppointmentRepositoryPort repository;
  private final AppointmentPolicy policy;

  public ListAppointmentsService(AppointmentRepositoryPort repository, AppointmentPolicy policy) {
    this.repository = repository;
    this.policy = policy;
  }

  public AppointmentPageView listAppointments(AppointmentActor actor, AppointmentPageQuery query) {
    if (query.page() < 0 || query.size() < 1 || query.size() > 100)
      throw AppointmentError.validation("Phân trang không hợp lệ (size từ 1 đến 100).");
    if (query.status() != null)
      try {
        AppointmentStatus.valueOf(query.status());
      } catch (IllegalArgumentException e) {
        throw AppointmentError.validation("Trạng thái không hợp lệ.");
      }
    return repository.page(policy.listing(actor, query));
  }
}
