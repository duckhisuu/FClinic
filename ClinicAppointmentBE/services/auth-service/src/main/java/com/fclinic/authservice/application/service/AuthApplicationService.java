package com.fclinic.authservice.application.service;

import com.fclinic.authservice.application.command.ChangePasswordCommand;
import com.fclinic.authservice.application.command.GoogleLoginCommand;
import com.fclinic.authservice.application.command.LoginCommand;
import com.fclinic.authservice.application.command.OAuth2CodeCommand;
import com.fclinic.authservice.application.command.RegisterCommand;
import com.fclinic.authservice.application.command.UpdateProfileCommand;
import com.fclinic.authservice.application.model.AuthResult;
import com.fclinic.authservice.application.model.KeycloakTokenClaims;
import com.fclinic.authservice.application.model.KeycloakTokenPair;
import com.fclinic.authservice.application.port.in.ChangePasswordUseCase;
import com.fclinic.authservice.application.port.in.GetCurrentUserUseCase;
import com.fclinic.authservice.application.port.in.GoogleLoginUseCase;
import com.fclinic.authservice.application.port.in.LoginUseCase;
import com.fclinic.authservice.application.port.in.LogoutUseCase;
import com.fclinic.authservice.application.port.in.OAuth2CodeLoginUseCase;
import com.fclinic.authservice.application.port.in.RefreshTokenUseCase;
import com.fclinic.authservice.application.port.in.RegisterUseCase;
import com.fclinic.authservice.application.port.in.UpdateProfileUseCase;
import com.fclinic.authservice.application.port.out.KeycloakIdentityPort;
import com.fclinic.authservice.application.port.out.UserRepositoryPort;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.exception.AccountDisabledException;
import com.fclinic.authservice.domain.exception.AuthValidationException;
import com.fclinic.authservice.domain.exception.InvalidCredentialsException;
import com.fclinic.authservice.domain.exception.InvalidTokenException;
import com.fclinic.authservice.domain.exception.UserAlreadyExistsException;
import com.fclinic.authservice.domain.exception.UserNotFoundException;
import com.fclinic.authservice.domain.util.UuidV4;
import com.fclinic.authservice.domain.valueobject.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthApplicationService implements
        RegisterUseCase,
        LoginUseCase,
        GoogleLoginUseCase,
        OAuth2CodeLoginUseCase,
        RefreshTokenUseCase,
        LogoutUseCase,
        GetCurrentUserUseCase,
        UpdateProfileUseCase,
        ChangePasswordUseCase {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepositoryPort userRepository;
    private final KeycloakIdentityPort keycloakIdentityPort;
    private final UserProvisioningSupport provisioningSupport;

    public AuthApplicationService(
            UserRepositoryPort userRepository,
            KeycloakIdentityPort keycloakIdentityPort,
            UserProvisioningSupport provisioningSupport
    ) {
        this.userRepository = userRepository;
        this.keycloakIdentityPort = keycloakIdentityPort;
        this.provisioningSupport = provisioningSupport;
    }

    @Override
    @Transactional
    public AuthResult register(RegisterCommand command) {
        User created = provisioningSupport.provisionNewUser(
                command.username(),
                command.password(),
                command.fullName(),
                command.email(),
                command.phoneNumber(),
                RoleName.ROLE_PATIENT,
                command.patientId(),
                null,
                null
        );
        KeycloakTokenPair tokens = keycloakIdentityPort.loginWithPassword(created.getUsername(), command.password());
        return toAuthResult(tokens, created);
    }

    @Override
    @Transactional
    public AuthResult login(LoginCommand command) {
        String identifier = command.username().trim().toLowerCase();
        Optional<User> preloaded = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier));
        if (preloaded.isPresent() && !preloaded.get().isActive()) {
            throw new AccountDisabledException("Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ quản trị viên");
        }

        KeycloakTokenPair tokens = keycloakIdentityPort.loginWithPassword(identifier, command.password());
        KeycloakTokenClaims claims = keycloakIdentityPort.introspectToken(tokens.accessToken());
        User user = provisioningSupport.resolveOrSyncUserFromClaims(claims);
        if (!user.isActive()) {
            throw new AccountDisabledException("Tài khoản đã bị vô hiệu hóa");
        }
        return toAuthResult(tokens, user);
    }

    @Override
    @Transactional
    public AuthResult exchangeCode(OAuth2CodeCommand command) {
        KeycloakTokenPair tokens = keycloakIdentityPort.exchangeAuthorizationCode(
                command.code(),
                command.redirectUri(),
                command.codeVerifier(),
                command.clientId()
        );
        KeycloakTokenClaims claims = keycloakIdentityPort.introspectToken(tokens.accessToken());
        if (!claims.active()) {
            throw new InvalidTokenException("Token trao đổi từ Keycloak không hợp lệ");
        }
        User user = provisioningSupport.resolveOrSyncUserFromClaims(claims);
        return toAuthResult(tokens, user);
    }

    @Override
    @Transactional
    public AuthResult loginWithGoogle(GoogleLoginCommand command) {
        if (command.authorizationCode() != null && !command.authorizationCode().isBlank()) {
            return exchangeCode(new OAuth2CodeCommand(
                    command.authorizationCode(),
                    command.redirectUri(),
                    command.codeVerifier(),
                    null
            ));
        }

        if (command.email() == null || command.email().isBlank() || command.googleId() == null || command.googleId().isBlank()) {
            throw new AuthValidationException("Cần cung cấp authorizationCode hoặc email + googleId để đăng nhập Google");
        }

        String email = command.email().trim().toLowerCase();
        String googleId = command.googleId().trim();
        String displayName = (command.name() != null && !command.name().isBlank())
                ? command.name().trim()
                : email.substring(0, email.indexOf('@'));

        User user = userRepository.findByGoogleId(googleId)
                .or(() -> userRepository.findByEmail(email))
                .orElseGet(() -> provisioningSupport.provisionNewUser(
                        deriveGoogleUsername(email),
                        generateInternalSecret(),
                        displayName,
                        email,
                        null,
                        RoleName.ROLE_PATIENT,
                        null,
                        null,
                        googleId
                ));

        if (!user.isActive()) {
            throw new AccountDisabledException("Tài khoản đã bị vô hiệu hóa");
        }

        if (user.getGoogleId() == null || !googleId.equals(user.getGoogleId())) {
            user.linkGoogleId(googleId);
            user = userRepository.save(user);
            keycloakIdentityPort.linkFederatedGoogleIdentity(user.getKeycloakId(), googleId, email);
        }

        KeycloakTokenPair tokens = keycloakIdentityPort.issueTokenForFederatedUser(user.getKeycloakId(), user.getUsername());
        return toAuthResult(tokens, user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthValidationException("Refresh token không được để trống");
        }
        KeycloakTokenPair tokens = keycloakIdentityPort.refreshToken(refreshToken.trim());
        KeycloakTokenClaims claims = keycloakIdentityPort.introspectToken(tokens.accessToken());
        if (!claims.active()) {
            throw new InvalidTokenException("Token làm mới không hợp lệ hoặc đã hết hạn");
        }
        User user = provisioningSupport.resolveOrSyncUserFromClaims(claims);
        return toAuthResult(tokens, user);
    }

    @Override
    public void logout(String refreshToken, UUID currentUserId) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            keycloakIdentityPort.logoutSession(refreshToken.trim());
        } else if (currentUserId != null) {
            userRepository.findById(UuidV4.requireValid(currentUserId, "userId"))
                    .ifPresent(user -> keycloakIdentityPort.revokeAllUserSessions(user.getKeycloakId()));
        } else {
            throw new AuthValidationException("Cần cung cấp refreshToken hoặc phiên đăng nhập để đăng xuất");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public User getCurrentUser(UUID userId) {
        UUID validId = UuidV4.requireValid(userId, "userId");
        return userRepository.findById(validId)
                .orElseThrow(() -> new UserNotFoundException("Không tìm thấy người dùng: " + validId));
    }

    @Override
    @Transactional
    public User updateProfile(UUID userId, UpdateProfileCommand command) {
        User user = getCurrentUser(userId);

        if (command.email() != null && !command.email().isBlank()) {
            String nextEmail = command.email().trim().toLowerCase();
            if (!nextEmail.equals(user.getEmail()) && userRepository.existsByEmail(nextEmail)) {
                throw new UserAlreadyExistsException("Email đã được sử dụng: " + nextEmail);
            }
        }
        if (command.phoneNumber() != null && !command.phoneNumber().isBlank()) {
            String nextPhone = command.phoneNumber().trim();
            if (!nextPhone.equals(user.getPhoneNumber()) && userRepository.existsByPhoneNumber(nextPhone)) {
                throw new UserAlreadyExistsException("Số điện thoại đã được sử dụng: " + nextPhone);
            }
        }

        user.updateProfile(command.fullName(), command.email(), command.phoneNumber());
        User saved = userRepository.save(user);
        keycloakIdentityPort.updateUserProfile(
                saved.getKeycloakId(),
                saved.getEmail(),
                saved.getFullName(),
                saved.getPhoneNumber(),
                saved.getPatientId(),
                saved.getDoctorId()
        );
        return saved;
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordCommand command) {
        User user = getCurrentUser(userId);
        if (command.oldPassword().equals(command.newPassword())) {
            throw new AuthValidationException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        try {
            keycloakIdentityPort.loginWithPassword(user.getUsername(), command.oldPassword());
        } catch (InvalidCredentialsException ex) {
            throw new InvalidCredentialsException("Mật khẩu hiện tại không chính xác");
        }
        keycloakIdentityPort.resetPassword(user.getKeycloakId(), command.newPassword());
    }

    private AuthResult toAuthResult(KeycloakTokenPair tokens, User user) {
        String tokenType = (tokens.tokenType() == null || tokens.tokenType().isBlank()) ? "Bearer" : tokens.tokenType();
        return new AuthResult(tokens.accessToken(), tokens.refreshToken(), tokenType, tokens.expiresIn(), user);
    }

    private String deriveGoogleUsername(String email) {
        String prefix = email.substring(0, email.indexOf('@'))
                .replaceAll("[^a-z0-9._-]", "")
                .toLowerCase();
        if (prefix.length() < 3) {
            prefix = "google_" + prefix;
        }
        if (!userRepository.existsByUsername(prefix)) {
            return prefix;
        }
        return prefix + "_" + UuidV4.generate().toString().substring(0, 6);
    }

    private String generateInternalSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "Fc#G0_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

