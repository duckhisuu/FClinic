package com.fclinic.authservice.application.port.out;

import com.fclinic.authservice.application.model.KeycloakProvisionCommand;
import com.fclinic.authservice.application.model.KeycloakTokenClaims;
import com.fclinic.authservice.application.model.KeycloakTokenPair;
import com.fclinic.authservice.domain.valueobject.RoleName;

import java.util.Optional;
import java.util.UUID;

public interface KeycloakIdentityPort {

    UUID createUser(KeycloakProvisionCommand command);

    void deleteUser(UUID keycloakUserId);

    Optional<UUID> findUserIdByUsernameOrEmail(String identifier);

    KeycloakTokenPair loginWithPassword(String username, String password);

    KeycloakTokenPair exchangeAuthorizationCode(String code, String redirectUri, String codeVerifier, String clientId);

    KeycloakTokenPair refreshToken(String refreshToken);

    void logoutSession(String refreshToken);

    void revokeAllUserSessions(UUID keycloakUserId);

    KeycloakTokenClaims introspectToken(String accessToken);

    void updateUserProfile(
            UUID keycloakUserId,
            String email,
            String fullName,
            String phoneNumber,
            UUID patientId,
            UUID doctorId
    );

    void setUserEnabled(UUID keycloakUserId, boolean enabled);

    void replaceRealmRole(UUID keycloakUserId, RoleName targetRole);

    void resetPassword(UUID keycloakUserId, String newPassword);

    void linkFederatedGoogleIdentity(UUID keycloakUserId, String googleUserId, String googleUsername);

    KeycloakTokenPair issueTokenForFederatedUser(UUID keycloakUserId, String username);
}

