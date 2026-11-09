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
    final AuthApplicationService service = new AuthApplicationService(users, keycloak, provision);

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
