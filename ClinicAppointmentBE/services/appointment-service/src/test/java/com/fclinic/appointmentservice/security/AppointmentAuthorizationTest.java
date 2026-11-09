package com.fclinic.appointmentservice.security;

import static com.fclinic.appointmentservice.security.SecurityFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.security.AppointmentPolicy;
import com.fclinic.appointmentservice.application.usecase.*;
import com.fclinic.appointmentservice.domain.AppointmentError;
import java.util.*;
import org.junit.jupiter.api.*;

class AppointmentAuthorizationTest {
  final AppointmentScopePort scopes = mock(AppointmentScopePort.class);
  final AppointmentPolicy policy = new AppointmentPolicy(scopes);
  final AppointmentRepositoryPort repository = mock(AppointmentRepositoryPort.class);
  final IdempotentAppointmentOperation operations = mock(IdempotentAppointmentOperation.class);
  @BeforeEach void existing() { when(repository.findById(ID)).thenReturn(Optional.of(appointment())); }
  void denied(Runnable work) { assertThatThrownBy(work::run).isInstanceOfSatisfying(AppointmentError.class, e -> assertThat(e.status()).isEqualTo(403)); }
  @Test void selfAndAssignedDoctorReadOnlyTheirResources() {
    var detail = new GetAppointmentService(repository, policy);
    assertThat(detail.getById(alice(), ID).patientId()).isEqualTo(PATIENT);
    assertThat(detail.getById(actor("ROLE_DOCTOR", null, DOCTOR), ID).doctorId()).isEqualTo(DOCTOR);
    denied(() -> detail.getById(actor("ROLE_PATIENT", OTHER, null), ID));
    denied(() -> detail.getById(actor("ROLE_DOCTOR", null, OTHER), ID));
    denied(() -> detail.getById(actor("ROLE_ADMIN", null, null), ID));
  }
  @Test void listScopesApplyBeforeRepositoryPagination() {
    var list = new ListAppointmentsService(repository, policy);
    list.listAppointments(alice(), new AppointmentPageQuery(null, null, null, null, 0, 20));
    verify(repository).page(argThat(q -> PATIENT.equals(q.patientId())));
    var staff = actor("ROLE_STAFF", null, null);
    when(scopes.patients(staff)).thenReturn(Set.of(PATIENT));
    list.listAppointments(staff, new AppointmentPageQuery(null, null, null, null, 0, 20));
    verify(repository).page(argThat(q -> Set.of(PATIENT).equals(q.allowedPatientIds())));
    denied(() -> list.listAppointments(alice(), new AppointmentPageQuery(OTHER, null, null, null, 0, 20)));
  }
  @Test void cancelledReplayStillRequiresCurrentOwnershipAndStaffScope() {
    var cancel = new CancelAppointmentService(repository, mock(UnitOfWorkPort.class), mock(AppointmentHistoryPort.class),
        mock(AppointmentEventPublisherPort.class), operations, CLOCK, policy);
    denied(() -> cancel.cancelAppointment(new CancelAppointmentCommand(actor("ROLE_PATIENT", OTHER, null), UUID.randomUUID(), ID, 0, null)));
    var staff = actor("ROLE_STAFF", null, null);
    when(scopes.allowed(staff, "APPOINTMENT_CANCEL", PATIENT)).thenReturn(true, false);
    var command = new CancelAppointmentCommand(staff, UUID.randomUUID(), ID, 0, null);
    cancel.cancelAppointment(command);
    denied(() -> cancel.cancelAppointment(command));
    verify(operations, times(1)).execute(any(), any(), any(), any(), any(), any());
  }
  @Test void bookingAndRescheduleDenyBeforeReplayOrProviders() {
    var patients = mock(PatientReferencePort.class); var doctors = mock(DoctorReferencePort.class);
    var book = new AppointmentApplicationService(repository, mock(UnitOfWorkPort.class), mock(AppointmentHistoryPort.class),
        mock(AppointmentEventPublisherPort.class), operations, CLOCK, patients, doctors, policy);
    denied(() -> book.bookAppointment(new BookAppointmentCommand(actor("ROLE_PATIENT", OTHER, null), UUID.randomUUID(), PATIENT, DOCTOR, UUID.randomUUID(), null)));
    var reschedule = new RescheduleAppointmentService(repository, mock(UnitOfWorkPort.class), mock(AppointmentHistoryPort.class),
        mock(AppointmentEventPublisherPort.class), operations, CLOCK, doctors, policy);
    denied(() -> reschedule.rescheduleAppointment(new RescheduleAppointmentCommand(actor("ROLE_DOCTOR", null, DOCTOR), UUID.randomUUID(), ID, 0, UUID.randomUUID(), null)));
    verifyNoInteractions(operations, patients, doctors);
  }
  @Test void clinicalReferenceIsMachineOnlyAndLeastData() {
    var ref = new GetAppointmentReferenceService(repository, policy);
    denied(() -> ref.medicalReference(alice(), ID));
    var patientService = new com.fclinic.appointmentservice.application.AppointmentActor("machine", "SERVICE", Set.of("SVC_APPOINTMENT_READ"), null, null, "patient-service", Long.MAX_VALUE);
    assertThat(ref.medicalReference(patientService, ID).id()).isEqualTo(ID);
    denied(() -> ref.reminderReference(patientService, ID));
  }
}
