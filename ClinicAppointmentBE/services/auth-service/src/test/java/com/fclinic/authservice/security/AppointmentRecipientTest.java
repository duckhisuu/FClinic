package com.fclinic.authservice.security;

import com.fclinic.authservice.api.controller.AppointmentRecipientController;
import com.fclinic.authservice.application.port.out.UserRepositoryPort;
import com.fclinic.authservice.application.service.AppointmentRecipientService;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.valueobject.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AppointmentRecipientTest {
  UUID patient = UUID.randomUUID(), doctor = UUID.randomUUID();
  UserRepositoryPort users = mock(UserRepositoryPort.class);
  AppointmentRecipientService service = new AppointmentRecipientService(users);
  User account(RoleName role, UUID patientId, UUID doctorId) {
    return User.create(UUID.randomUUID(), UUID.randomUUID(), "synthetic", "contact@example.test", null, "Contact",
        new Role(UUID.randomUUID(), role, "", Instant.now(), Instant.now()), patientId, doctorId, null);
  }
  @Test void preservesUnlinkedPatientAndReturnsExactDoctorAccount() {
    User doctorAccount = account(RoleName.ROLE_DOCTOR, null, doctor);
    when(users.findPatientAccounts(patient)).thenReturn(List.of()); when(users.findDoctorAccounts(doctor)).thenReturn(List.of(doctorAccount));
    var result = service.resolve(patient, doctor);
    assertThat(result.patientUserId()).isNull(); assertThat(result.doctor().userId()).isEqualTo(doctorAccount.getId()).isNotEqualTo(doctor);
    verify(users, never()).findByEmail(anyString()); verify(users, never()).findByPhoneNumber(anyString());
  }
  @Test void missingAmbiguousOrInactiveMappingsFailClosed() {
    when(users.findPatientAccounts(patient)).thenReturn(List.of()); when(users.findDoctorAccounts(doctor)).thenReturn(List.of());
    assertThatThrownBy(() -> service.resolve(patient, doctor)).isInstanceOf(AppointmentRecipientService.UnresolvedMapping.class);
    var d = account(RoleName.ROLE_DOCTOR, null, doctor);
    when(users.findDoctorAccounts(doctor)).thenReturn(List.of(d, d));
    assertThatThrownBy(() -> service.resolve(patient, doctor)).isInstanceOf(AppointmentRecipientService.UnresolvedMapping.class);
    when(users.findDoctorAccounts(doctor)).thenReturn(List.of(d));
    var p = account(RoleName.ROLE_PATIENT, patient, null); when(users.findPatientAccounts(patient)).thenReturn(List.of(p, p));
    assertThatThrownBy(() -> service.resolve(patient, doctor)).isInstanceOf(AppointmentRecipientService.UnresolvedMapping.class);
  }
  @Test void endpointRejectsHumanMissingWrongAndDuplicateCredentials() throws Exception {
    var env = new MockEnvironment().withProperty("app.recipients.client-id", "recipient").withProperty("app.recipients.client-secret", "dedicated");
    var mvc = MockMvcBuilders.standaloneSetup(new AppointmentRecipientController(service, env)).build();
    String body = "{\"patientId\":\""+patient+"\",\"doctorId\":\""+doctor+"\"}";
    for (String header : List.of("Bearer synthetic", "Basic bad", ""))
      mvc.perform(post(AppointmentRecipientController.PATH).contentType("application/json").content(body).header("Authorization", header)).andExpect(status().isUnauthorized());
    String auth = "Basic " + Base64.getEncoder().encodeToString("recipient:dedicated".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    mvc.perform(post(AppointmentRecipientController.PATH).contentType("application/json").content(body).header("Authorization", auth, auth)).andExpect(status().isUnauthorized());
    when(users.findPatientAccounts(patient)).thenReturn(List.of()); when(users.findDoctorAccounts(doctor)).thenReturn(List.of(account(RoleName.ROLE_DOCTOR, null, doctor)));
    mvc.perform(post(AppointmentRecipientController.PATH).contentType("application/json").content(body).header("Authorization", auth))
        .andExpect(status().isOk()).andExpect(header().string("X-FClinic-Reference-Contract", "appointment-recipient-accounts/1"))
        .andExpect(jsonPath("$.data.patientUserId").doesNotExist());
  }
}
