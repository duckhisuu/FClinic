package com.fclinic.appointmentservice.security;

import static com.fclinic.appointmentservice.security.SecurityFixtures.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fclinic.appointmentservice.api.*;
import com.fclinic.appointmentservice.api.controller.AppointmentController;
import com.fclinic.appointmentservice.api.exception.GlobalExceptionHandler;
import com.fclinic.appointmentservice.application.port.in.*;
import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.security.AppointmentPolicy;
import com.fclinic.appointmentservice.application.usecase.*;
import com.fclinic.appointmentservice.infrastructure.security.AuthIdentityVerifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AppointmentSecurityMvcTest {
  final AppointmentRepositoryPort repository = mock(AppointmentRepositoryPort.class);
  final AuthIdentityVerifier identity = mock(AuthIdentityVerifier.class);
  MockMvc mvc;
  String basic(String user, String secret) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + secret).getBytes(StandardCharsets.UTF_8));
  }
  @BeforeEach void setup() {
    var policy = new AppointmentPolicy(mock(AppointmentScopePort.class));
    var controller = new AppointmentController(mock(BookAppointmentUseCase.class), mock(CancelAppointmentUseCase.class),
        mock(RescheduleAppointmentUseCase.class), new GetAppointmentService(repository, policy),
        new ListAppointmentsService(repository, policy), mock(GetAvailableSlotsUseCase.class),
        new GetAppointmentHistoryService(repository, mock(AppointmentHistoryPort.class), policy), new GetAppointmentReferenceService(repository, policy));
    var auth = new AppointmentAuthentication(identity, settings(null));
    mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())
        .addInterceptors(auth).addFilters(new AppointmentRequestIdFilter()).build();
    when(repository.findById(ID)).thenReturn(Optional.of(appointment()));
    when(identity.verify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong()))
        .thenAnswer(i -> { if ("Bearer alice".equals(i.getArgument(0))) return alice();
          if ("Bearer bob".equals(i.getArgument(0))) return actor("ROLE_PATIENT", OTHER, null);
          throw AuthIdentityVerifier.unauthorized(); });
  }
  @Test void anonymousAndForgedIdentityHeadersAre401AtDirectService() throws Exception {
    mvc.perform(get("/api/v2/appointments/" + ID).header("X-User-Id", PATIENT).header("X-Role", "ROLE_ADMIN"))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mvc.perform(get("/api/v2/appointments/" + ID).header("Authorization", "Bearer alice", "Bearer bob"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(repository, identity);
  }
  @Test void ownerGetsDataButOtherPatientCannotReadDetailOrHistory() throws Exception {
    mvc.perform(get("/api/v2/appointments/" + ID).header("Authorization", "Bearer alice"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.patientId").value(PATIENT.toString()));
    mvc.perform(get("/api/v2/appointments/" + ID).header("Authorization", "Bearer bob"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").isEmpty());
    mvc.perform(get("/api/v2/appointments/" + ID + "/history").header("Authorization", "Bearer bob"))
        .andExpect(status().isForbidden());
  }
  @Test void patientBasicCredentialIsRestrictedToMedicalReference() throws Exception {
    String credential = basic("patient-reference", "patient-test-secret");
    mvc.perform(get("/api/v2/appointments/" + ID + "/medical-reference").header("Authorization", credential))
        .andExpect(status().isOk()).andExpect(header().string("X-FClinic-Reference-Contract", "patient-appointment-medical-reference/2"))
        .andExpect(jsonPath("$.data.length()").value(4)).andExpect(jsonPath("$.data.reasonForVisit").doesNotExist());
    mvc.perform(get("/api/v2/appointments/" + ID).header("Authorization", credential)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v2/appointments/" + ID + "/reminder-reference").header("Authorization", credential)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v2/appointments/" + ID + "/medical-reference").header("Authorization", basic("patient-reference", "wrong")))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v2/appointments/" + ID + "/medical-reference").header("Authorization", "Bearer alice"))
        .andExpect(status().isUnauthorized());
  }
  @Test void versionHeaderIsPreservedOnReference401And404() throws Exception {
    mvc.perform(get("/api/v2/appointments/" + ID + "/medical-reference"))
        .andExpect(status().isUnauthorized()).andExpect(header().string("X-FClinic-Reference-Contract", "patient-appointment-medical-reference/2"));
    when(repository.findById(ID)).thenReturn(Optional.empty());
    mvc.perform(get("/api/v2/appointments/" + ID + "/medical-reference").header("Authorization", basic("patient-reference", "patient-test-secret")))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_FOUND"))
        .andExpect(header().string("X-FClinic-Reference-Contract", "patient-appointment-medical-reference/2"));
  }
  @Test void authOutageIs503RatherThanAnonymousAccess() throws Exception {
    doThrow(com.fclinic.appointmentservice.domain.AppointmentError.unavailable()).when(identity)
        .verify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    mvc.perform(get("/api/v2/appointments/" + ID).header("Authorization", "Bearer candidate"))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    verifyNoInteractions(repository);
  }
}
