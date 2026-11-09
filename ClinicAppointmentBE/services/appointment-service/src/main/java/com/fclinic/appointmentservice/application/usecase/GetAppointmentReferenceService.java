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
public class GetAppointmentReferenceService implements GetAppointmentReferenceUseCase {
  private final AppointmentRepositoryPort repository;
  private final AppointmentPolicy policy;

  public GetAppointmentReferenceService(AppointmentRepositoryPort repository, AppointmentPolicy policy) {
    this.repository = repository;
    this.policy = policy;
  }

  public MedicalReferenceView medicalReference(AppointmentActor actor, UUID id) {
    policy.reference(actor, "patient-service", "SVC_APPOINTMENT_READ");
    var a = repository.findById(id).orElseThrow(AppointmentNotFoundException::new);
    return new MedicalReferenceView(
        a.getId(), a.getPatientId(), a.getDoctorId(), a.getStatus().name());
  }

  public ReminderReferenceView reminderReference(AppointmentActor actor, UUID id) {
    policy.reference(actor, "notification-service", "SVC_APPOINTMENT_REMINDER_READ");
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
