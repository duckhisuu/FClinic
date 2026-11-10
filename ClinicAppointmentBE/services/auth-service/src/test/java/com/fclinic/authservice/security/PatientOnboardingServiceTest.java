package com.fclinic.authservice.security;

import com.fclinic.authservice.application.model.*;
import com.fclinic.authservice.application.port.out.*;
import com.fclinic.authservice.application.service.*;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.valueobject.*;
import com.fclinic.authservice.domain.exception.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import com.fclinic.authservice.infrastructure.config.KeycloakJwtAuthenticationConverter;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PatientOnboardingServiceTest {
    final UserRepositoryPort users=mock(UserRepositoryPort.class);
    final KeycloakIdentityPort keycloak=mock(KeycloakIdentityPort.class);
    final PatientOnboardingPort store=mock(PatientOnboardingPort.class);
    final PatientProvisioningPort patients=mock(PatientProvisioningPort.class);
    final PatientOnboardingService service=new PatientOnboardingService(users,keycloak,store,patients);
    final Instant now=Instant.parse("2026-11-10T00:00:00Z");
    User user(RoleName role) { return User.create(UUID.randomUUID(),UUID.randomUUID(),"synthetic","synthetic@example.test",null,"Synthetic",
        new Role(UUID.randomUUID(),role,"test",now,now),null,null,null); }
    AuthenticatedCaller caller(User user) { return new AuthenticatedCaller(user.getId(),user.getKeycloakId(),user.getUsername(),user.getEmail(),user.getRoleName(),null,null); }
    @Test void downstreamFailurePreservesReservationAndRetryRepairsBeforeBinding() {
        User user=user(RoleName.ROLE_PATIENT); when(users.findById(user.getId())).thenReturn(Optional.of(user));
        var r=new PatientOnboardingPort.Reservation(user.getId(),UUID.randomUUID(),user.getKeycloakId(),"{}","{}","a".repeat(64));
        when(store.reserve(any(),any(),anyString(),anyString(),anyString())).thenReturn(r);
        doThrow(new RuntimeException("synthetic Keycloak outage")).doNothing().when(keycloak).updateUserProfile(any(),any(),any(),any(),any(),any());
        var command=new PatientOnboardingCommand("Synthetic","+84900000001","1995-01-01","OTHER",null);
        assertThatThrownBy(()->service.onboard(caller(user),command)).isInstanceOf(PatientOnboardingException.class);
        verify(store,never()).bind(any());
        service.onboard(caller(user),command);
        var order=inOrder(patients,keycloak,store);
        order.verify(patients).ensureProfile(r); order.verify(keycloak).updateUserProfile(any(),any(),any(),any(),eq(r.patientId()),any());
        order.verify(patients).ensureProfile(r); order.verify(keycloak).updateUserProfile(any(),any(),any(),any(),eq(r.patientId()),any());
        order.verify(store).bind(r);
    }
    @Test void inactiveStaffAndMismatchedSubjectsCannotReserveProfiles() {
        for(RoleName role:List.of(RoleName.ROLE_STAFF,RoleName.ROLE_ADMIN,RoleName.ROLE_DOCTOR)) {
            var user=user(role); when(users.findById(user.getId())).thenReturn(Optional.of(user));
            assertThatThrownBy(()->service.owner(caller(user))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
        var user=user(RoleName.ROLE_PATIENT); user.setActiveStatus(false); when(users.findById(user.getId())).thenReturn(Optional.of(user));
        assertThatThrownBy(()->service.owner(caller(user))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(store,patients,keycloak);
    }
    @Test void browserCannotSubmitAnyProfileOrAccountUuid() {
        assertThatThrownBy(()->PatientOnboardingCommand.parse(Map.of("patientId",UUID.randomUUID().toString()),Clock.systemUTC()))
            .isInstanceOf(AuthValidationException.class);
    }
    @Test void verifiedDifferentSubjectCannotInheritExistingAdminRoleByEmail() {
        var victim=user(RoleName.ROLE_ADMIN); when(users.findByEmail(victim.getEmail())).thenReturn(Optional.of(victim));
        var support=new UserProvisioningSupport(users,mock(RoleRepositoryPort.class),keycloak);
        var jwt=Jwt.withTokenValue("synthetic").header("alg","RS256").subject(UUID.randomUUID().toString())
            .claim("email",victim.getEmail()).issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        assertThatThrownBy(()->new KeycloakJwtAuthenticationConverter(support).convert(jwt))
            .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        verify(users,never()).save(any());
    }
}
