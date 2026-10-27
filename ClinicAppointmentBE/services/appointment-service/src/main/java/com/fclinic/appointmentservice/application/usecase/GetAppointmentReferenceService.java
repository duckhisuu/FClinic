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
public class GetAppointmentReferenceService implements GetAppointmentReferenceUseCase {
  private final AppointmentRepositoryPort repository;

  public GetAppointmentReferenceService(AppointmentRepositoryPort repository) {
    this.repository = repository;
  }

  public MedicalReferenceView medicalReference(long id) {
    var a = repository.findById(id).orElseThrow(AppointmentNotFoundException::new);
    return new MedicalReferenceView(
        a.getId(), a.getPatientId(), a.getDoctorId(), a.getStatus().name());
  }

  public ReminderReferenceView reminderReference(long id) {
    var a = repository.findById(id).orElseThrow(AppointmentNotFoundException::new);
    return new ReminderReferenceView(
        a.getId(),
        a.getPatientId(),
        a.getDoctorId(),
        a.getStatus().name(),
        a.getVersion(),
        a.getSlotId(),
        a.getAppointmentDate(),
        a.getStartTime(),
        a.getEndTime(),
        "Asia/Saigon");
  }
}
