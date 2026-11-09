package com.fclinic.appointmentservice.application.security;

import com.fclinic.appointmentservice.application.AppointmentActor;
import com.fclinic.appointmentservice.application.command.AppointmentPageQuery;
import com.fclinic.appointmentservice.application.port.out.AppointmentScopePort;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class AppointmentPolicy {
  private final AppointmentScopePort scopes;
  public AppointmentPolicy(AppointmentScopePort scopes) { this.scopes = scopes; }
  public void authenticated(AppointmentActor actor) {
    if (actor == null || actor.subject() == null || actor.subject().isBlank())
      throw new AppointmentError(401, "UNAUTHENTICATED", "Yêu cầu xác thực hợp lệ.");
  }
  public static void allow(boolean allowed) {
    if (!allowed) throw new AppointmentError(403, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này.");
  }
  public void mutate(AppointmentActor actor, UUID patient, String operation) {
    authenticated(actor);
    allow((actor.user("ROLE_PATIENT") && patient.equals(actor.patientId()))
        || (actor.user("ROLE_STAFF") && scopes.allowed(actor, operation, patient)));
  }
  public void read(AppointmentActor actor, Appointment appointment) {
    authenticated(actor);
    if (actor.user("ROLE_DOCTOR")) allow(appointment.getDoctorId().equals(actor.doctorId()));
    else mutate(actor, appointment.getPatientId(), "APPOINTMENT_READ");
  }
  public AppointmentPageQuery listing(AppointmentActor actor, AppointmentPageQuery q) {
    authenticated(actor);
    UUID patient = q.patientId(), doctor = q.doctorId();
    java.util.Set<UUID> permitted = null;
    if (actor.user("ROLE_PATIENT")) {
      allow(actor.patientId() != null && (patient == null || patient.equals(actor.patientId())));
      patient = actor.patientId();
    } else if (actor.user("ROLE_DOCTOR")) {
      allow(actor.doctorId() != null && (doctor == null || doctor.equals(actor.doctorId())));
      doctor = actor.doctorId();
    } else if (actor.user("ROLE_STAFF")) {
      permitted = scopes.patients(actor);
      if (permitted == null) throw AppointmentError.unavailable();
      if (patient != null) allow(permitted.contains(patient));
    } else allow(false);
    return new AppointmentPageQuery(patient, doctor, q.date(), q.status(), q.page(), q.size(), permitted);
  }
  public void availability(AppointmentActor actor, UUID doctor) {
    authenticated(actor);
    allow(actor.user("ROLE_PATIENT")
        || (actor.user("ROLE_DOCTOR") && doctor.equals(actor.doctorId()))
        || (actor.user("ROLE_STAFF") && scopes.allowed(actor, "APPOINTMENT_AVAILABILITY", null)));
  }
  public void reference(AppointmentActor actor, String service, String authority) {
    authenticated(actor);
    allow("SERVICE".equals(actor.principalType()) && service.equals(actor.serviceName())
        && actor.authorities().contains(authority));
  }
}
