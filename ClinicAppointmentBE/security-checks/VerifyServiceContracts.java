import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fclinic.authservice.api.mapper.AuthApiMapper;
import com.fclinic.authservice.application.model.*;
import com.fclinic.authservice.application.port.out.*;
import com.fclinic.authservice.application.service.*;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.valueobject.*;
import com.fclinic.appointmentservice.application.AppointmentActor;
import com.fclinic.appointmentservice.application.security.AppointmentPolicy;
import com.fclinic.appointmentservice.config.AppointmentSecuritySettings;
import com.fclinic.appointmentservice.infrastructure.security.*;
import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.infrastructure.adapter.identity.*;
import com.fclinic.patientservice.infrastructure.adapter.scope.*;
import java.net.URI;
import java.time.*;
import java.util.*;

/** Cross-module production producer/consumer contract checks; only storage and external IdP are mocked. */
public class VerifyServiceContracts {
  static void check(boolean ok, String description) { if (!ok) throw new AssertionError(description); System.out.println("PASS " + description); }
  public static void main(String[] args) {
    Clock clock = Clock.systemUTC(); Instant now = clock.instant();
    UUID subject = UUID.randomUUID(), patient = UUID.randomUUID(), staffSubject = UUID.randomUUID(), doctor = UUID.randomUUID();
    String machineSubject = UUID.randomUUID().toString();
    var users = mock(UserRepositoryPort.class); var keycloak = mock(KeycloakIdentityPort.class);
    var patientUser = User.create(UUID.randomUUID(), subject, "synthetic_patient", "patient@example.test", null, "Patient",
        new Role(UUID.randomUUID(), RoleName.ROLE_PATIENT, "", now, now), patient, null, null);
    var staffUser = User.create(UUID.randomUUID(), staffSubject, "synthetic_staff", "staff@example.test", null, "Staff",
        new Role(UUID.randomUUID(), RoleName.ROLE_STAFF, "", now, now), null, null, null);
    when(users.findByKeycloakId(subject)).thenReturn(Optional.of(patientUser)); when(users.findByKeycloakId(staffSubject)).thenReturn(Optional.of(staffUser));
    when(keycloak.introspectToken("human")).thenReturn(new KeycloakTokenClaims(true, subject.toString(), subject,
        patientUser.getUsername(), patientUser.getEmail(), "Patient", "https://issuer.example.test", List.of("patient-service", "appointment-service"),
        now.plusSeconds(300).getEpochSecond(), "fclinic-web", Set.of("ROLE_PATIENT"), patientUser.getId(), patient, null, false));
    when(keycloak.introspectToken("machine")).thenReturn(new KeycloakTokenClaims(true, machineSubject, UUID.fromString(machineSubject),
        "service-account-appointment-service", null, null, "https://issuer.example.test", List.of("patient-service"),
        now.plusSeconds(300).getEpochSecond(), "appointment-service", Set.of("SVC_PATIENT_READ"), null, null, null, true));
    var tokens = new TokenVerificationService(keycloak, users, new IntrospectionPolicy("https://issuer.example.test", List.of()), clock);
    var mapper = new AuthApiMapper();
    var identitySettings = new IdentitySettings(true, URI.create("https://auth.example.test/introspect"), "https://issuer.example.test", "patient-service",
        "patient-identity", "identity-secret", Duration.ofSeconds(1), Duration.ofSeconds(3), 65536, 16, null, null,
        Map.of("appointment", new IdentitySettings.ServiceBinding("appointment-service", Set.of("SVC_PATIENT_READ"), machineSubject)));
    identitySettings.validateEnabled();
    var decoder = new IntrospectionDecoder(identitySettings, clock);
    var machine = decoder.decode(AuthHttpTransport.JSON.writeValueAsBytes(mapper.toIntrospectionWireResponse(tokens.introspect("machine"))));
    check(machine.principalType() == TrustedCaller.PrincipalType.SERVICE && "appointment-service".equals(machine.serviceName()), "Auth introspection -> Patient registered SERVICE identity");
    var humanWire = AuthHttpTransport.JSON.valueToTree(mapper.toIntrospectionWireResponse(tokens.introspect("human")));
    var transport = mock(AuthHttpTransport.class);
    when(transport.post(any(), any(), any(), any(), any(), anyLong())).thenReturn(humanWire);
    var security = new AppointmentSecuritySettings(URI.create("https://auth.example.test/introspect"), "https://issuer.example.test", "appointment-service",
        "appointment-identity", "appointment-identity-secret", URI.create("https://auth.example.test/scope"), "appointment-scope", "scope-secret",
        "patient-reference", "reference-secret", null, null, null, null, null);
    var human = new AuthIdentityVerifier(transport, security, clock).verify("Bearer human", Long.MAX_VALUE);
    check(patient.equals(human.patientId()), "Auth introspection -> Appointment trusted self mapping");
    patientUser.setActiveStatus(false);
    check(!tokens.introspect("human").active(), "Account deactivation propagates through introspection"); patientUser.setActiveStatus(true);

    var assignments = mock(ScopeAssignmentPort.class);
    when(assignments.services()).thenReturn(Map.of("appointment", new ScopeAssignmentPort.ServiceIdentity(machineSubject, "appointment-service", Set.of("SVC_PATIENT_READ"))));
    when(assignments.grants()).thenReturn(Map.of(
        UUID.randomUUID(), new ScopeAssignmentPort.Grant(machineSubject, Set.of("APPOINTMENT_PROFILE_READ"), Set.of(), true, true, now.minusSeconds(60), now.plusSeconds(600), null, null, null),
        UUID.randomUUID(), new ScopeAssignmentPort.Grant(staffSubject.toString(), Set.of("APPOINTMENT_SEARCH", "APPOINTMENT_CANCEL"), Set.of(patient), false, true, now.minusSeconds(60), now.plusSeconds(600), null, null, null)));
    var scope = new ScopeAuthorityService(users, assignments, clock);
    var patientQuery = ScopeQuery.create(ScopeQuery.Authority.ADMINISTRATIVE, ScopeQuery.Operation.APPOINTMENT_PROFILE_READ, machine, patient, null, null, now);
    var authQuery = AuthHttpTransport.JSON.readValue(AuthHttpTransport.JSON.writeValueAsBytes(patientQuery.wire()), ScopeAuthorityService.Query.class);
    var scopeWire = AuthHttpTransport.JSON.writeValueAsBytes(scope.evaluate(authQuery));
    check(new ScopeDecisionDecoder(clock).decode(scopeWire, patientQuery, 1000).allowed(), "Auth scope provider -> Patient exact echo/evidence contract");
    when(transport.post(any(), any(), any(), any(), any(), anyLong())).thenAnswer(invocation -> {
      var query = AuthHttpTransport.JSON.readValue((String) invocation.getArgument(4), ScopeAuthorityService.Query.class);
      return AuthHttpTransport.JSON.valueToTree(scope.evaluate(query));
    });
    var staff = new AppointmentActor(staffSubject.toString(), "USER", Set.of("ROLE_STAFF"), null, null, null, Long.MAX_VALUE);
    var appointmentScopes = new AuthAppointmentScopeAdapter(transport, security, clock);
    check(appointmentScopes.patients(staff).equals(Set.of(patient)) && appointmentScopes.allowed(staff, "APPOINTMENT_CANCEL", patient), "Auth assignments -> Appointment scoped list and cancellation");
    check(!appointmentScopes.allowed(staff, "APPOINTMENT_CANCEL", UUID.randomUUID()), "Cross-patient STAFF action denied by actual Auth provider");

    var repository = mock(com.fclinic.appointmentservice.application.port.out.AppointmentRepositoryPort.class);
    UUID appointmentId = UUID.randomUUID();
    var appointment = new com.fclinic.appointmentservice.domain.aggregate.Appointment(appointmentId,
        com.fclinic.appointmentservice.domain.vo.BookingCode.generate(), patient, "Patient", "+84900000001", doctor, "Doctor", "General", UUID.randomUUID(),
        LocalDate.now(clock).plusDays(1), LocalTime.of(9,0), LocalTime.of(10,0), "Private reason", java.math.BigDecimal.TEN,
        com.fclinic.appointmentservice.domain.model.AppointmentStatus.CONFIRMED, 0, now, now);
    when(repository.findById(appointmentId)).thenReturn(Optional.of(appointment));
    var reference = new com.fclinic.appointmentservice.application.usecase.GetAppointmentReferenceService(repository, new AppointmentPolicy(appointmentScopes));
    var caller = new AppointmentActor("service:patient-service", "SERVICE", Set.of("SVC_APPOINTMENT_READ"), null, null, "patient-service", Long.MAX_VALUE);
    var controller = new com.fclinic.appointmentservice.api.controller.AppointmentController(null, null, null, null, null, null, null, reference);
    byte[] referenceWire = AuthHttpTransport.JSON.writeValueAsBytes(controller.medicalReference(caller, appointmentId));
    var decodedReference = new com.fclinic.patientservice.infrastructure.adapter.reference.AppointmentReferenceDecoder().decode(200, referenceWire, appointmentId).orElseThrow();
    check(decodedReference.patientId().equals(patient) && decodedReference.doctorId().equals(doctor), "Appointment projection -> Patient UUID reference decoder");
    check(!new String(referenceWire, java.nio.charset.StandardCharsets.UTF_8).contains("Private reason"), "Clinical reference excludes visit reason");
  }
}
