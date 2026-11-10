package com.fclinic.authservice.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fclinic.authservice.application.command.*;
import com.fclinic.authservice.application.port.out.*;
import com.fclinic.authservice.application.service.*;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.exception.AuthValidationException;
import com.fclinic.authservice.domain.valueobject.RoleName;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicIdentityBoundaryTest {
    final UserRepositoryPort users = mock(UserRepositoryPort.class);
    final KeycloakIdentityPort keycloak = mock(KeycloakIdentityPort.class);
    final UserProvisioningSupport provision = mock(UserProvisioningSupport.class);
    final PatientOwnershipPort ownership = mock(PatientOwnershipPort.class);
    final AuthApplicationService service = new AuthApplicationService(users, keycloak, provision, ownership);

    @Test void unprovenPatientUuidCannotBeClaimedOrRebound() {
        var id = UUID.randomUUID(); var user = mock(User.class);
        when(users.findById(id)).thenReturn(java.util.Optional.of(user));
        when(user.getRoleName()).thenReturn(RoleName.ROLE_PATIENT); when(user.isActive()).thenReturn(true);
        var candidate = UUID.randomUUID();
        assertThatThrownBy(() -> service.linkPatientProfile(id, candidate, "Bearer synthetic"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(user.getPatientId()).thenReturn(UUID.randomUUID());
        assertThatThrownBy(() -> service.linkPatientProfile(id, candidate, "Bearer synthetic"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(users, never()).save(any()); verifyNoInteractions(keycloak);
    }

    @Test void identicalTrustedBindingIsAnIdempotentRead() {
        var id = UUID.randomUUID(); var patient = UUID.randomUUID(); var user = mock(User.class);
        when(users.findById(id)).thenReturn(java.util.Optional.of(user));
        when(user.getRoleName()).thenReturn(RoleName.ROLE_PATIENT); when(user.isActive()).thenReturn(true); when(user.getPatientId()).thenReturn(patient);
        assertThat(service.linkPatientProfile(id, patient, "Bearer synthetic")).isSameAs(user);
        verify(users, never()).save(any()); verifyNoInteractions(keycloak);
    }

    @Test void verifiedSelfProfileIsPersistedAndSyncedToKeycloak() {
        var id = UUID.randomUUID(); var patient = UUID.randomUUID();
        var user = User.create(id, UUID.randomUUID(), "self_patient", "self@example.test", null,
                "Self Patient", new com.fclinic.authservice.domain.valueobject.Role(UUID.randomUUID(), RoleName.ROLE_PATIENT, null, null, null),
                null, null, null);
        when(users.findById(id)).thenReturn(java.util.Optional.of(user));
        when(users.lockById(id)).thenReturn(java.util.Optional.of(user));
        when(ownership.selfRegisteredPatient("Bearer verified")).thenReturn(patient);
        when(users.save(user)).thenReturn(user);
        assertThat(service.linkPatientProfile(id, patient, "Bearer verified").getPatientId()).isEqualTo(patient);
        verify(users).save(user);
        verify(keycloak).updateUserProfile(user.getKeycloakId(), user.getEmail(), user.getFullName(),
                user.getPhoneNumber(), patient, null);
    }

    @Test void ownershipProviderFailureDoesNotWriteAnAccount() {
        var id = UUID.randomUUID(); var user = mock(User.class);
        when(users.findById(id)).thenReturn(java.util.Optional.of(user));
        when(user.getRoleName()).thenReturn(RoleName.ROLE_PATIENT); when(user.isActive()).thenReturn(true);
        when(ownership.selfRegisteredPatient(anyString())).thenThrow(
                new com.fclinic.authservice.domain.exception.IdentityProviderException("unavailable"));
        assertThatThrownBy(() -> service.linkPatientProfile(id, UUID.randomUUID(), "Bearer verified"))
                .isInstanceOf(com.fclinic.authservice.domain.exception.IdentityProviderException.class);
        verify(users, never()).save(any()); verifyNoInteractions(keycloak);
    }

    @Test void selfDeclaredGoogleIdentityCannotObtainTokens() {
        assertThatThrownBy(() -> service.loginWithGoogle(new GoogleLoginCommand(
                "victim@example.test", "Victim", "unverified-id", null, null, null, null, null)))
                .isInstanceOf(AuthValidationException.class);
        verifyNoInteractions(users, keycloak, provision);
    }
    @Test void publicRegistrationNeverBindsAClientChosenPatient() {
        var stop = new AuthValidationException("stop before external provisioning");
        when(provision.provisionNewUser(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(stop);
        assertThatThrownBy(() -> service.register(new RegisterCommand("new_user", "synthetic-password", "New User",
                "new@example.test", null, UUID.randomUUID()))).isSameAs(stop);
        verify(provision).provisionNewUser("new_user", "synthetic-password", "New User", "new@example.test", null,
                RoleName.ROLE_PATIENT, null, null, null);
        verifyNoInteractions(keycloak);
    }
}
