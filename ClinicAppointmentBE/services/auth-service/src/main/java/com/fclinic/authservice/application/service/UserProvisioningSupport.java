package com.fclinic.authservice.application.service;

import com.fclinic.authservice.application.model.KeycloakProvisionCommand;
import com.fclinic.authservice.application.model.KeycloakTokenClaims;
import com.fclinic.authservice.application.port.out.KeycloakIdentityPort;
import com.fclinic.authservice.application.port.out.RoleRepositoryPort;
import com.fclinic.authservice.application.port.out.UserRepositoryPort;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.exception.AccountDisabledException;
import com.fclinic.authservice.domain.exception.RoleNotFoundException;
import com.fclinic.authservice.domain.exception.UserAlreadyExistsException;
import com.fclinic.authservice.domain.util.UuidV4;
import com.fclinic.authservice.domain.valueobject.Role;
import com.fclinic.authservice.domain.valueobject.RoleName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserProvisioningSupport {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningSupport.class);

    private final UserRepositoryPort userRepository;
    private final RoleRepositoryPort roleRepository;
    private final KeycloakIdentityPort keycloakIdentityPort;

    public UserProvisioningSupport(
            UserRepositoryPort userRepository,
            RoleRepositoryPort roleRepository,
            KeycloakIdentityPort keycloakIdentityPort
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.keycloakIdentityPort = keycloakIdentityPort;
    }

    public User provisionNewUser(
            String username,
            String password,
            String fullName,
            String email,
            String phoneNumber,
            RoleName roleName,
            UUID patientId,
            UUID doctorId,
            String googleId
    ) {
        String normalizedUsername = username.trim().toLowerCase();
        String normalizedEmail = email.trim().toLowerCase();
        String normalizedPhone = (phoneNumber == null || phoneNumber.isBlank()) ? null : phoneNumber.trim();

        validateUniqueness(normalizedUsername, normalizedEmail, normalizedPhone);

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RoleNotFoundException("Không tìm thấy vai trò: " + roleName));

        UUID userId = UuidV4.generate();
        UUID validPatientId = UuidV4.requireValidNullable(patientId, "patientId");
        UUID validDoctorId = UuidV4.requireValidNullable(doctorId, "doctorId");

        UUID keycloakId = keycloakIdentityPort.createUser(new KeycloakProvisionCommand(
                userId,
                normalizedUsername,
                normalizedEmail,
                password,
                fullName.trim(),
                roleName,
                validPatientId,
                validDoctorId,
                normalizedPhone,
                true
        ));

        try {
            if (googleId != null && !googleId.isBlank()) {
                keycloakIdentityPort.linkFederatedGoogleIdentity(keycloakId, googleId.trim(), normalizedEmail);
            }
            User user = User.create(
                    userId,
                    keycloakId,
                    normalizedUsername,
                    normalizedEmail,
                    normalizedPhone,
                    fullName.trim(),
                    role,
                    validPatientId,
                    validDoctorId,
                    googleId
            );
            return userRepository.save(user);
        } catch (RuntimeException ex) {
            compensateKeycloakUser(keycloakId);
            throw ex;
        }
    }

    public User resolveOrSyncUserFromClaims(KeycloakTokenClaims claims) {
        Optional<User> existing = Optional.empty();
        if (claims.keycloakUserId() != null) {
            existing = userRepository.findByKeycloakId(claims.keycloakUserId());
        }
        if (existing.isEmpty() && claims.fclinicUserId() != null) {
            existing = userRepository.findById(claims.fclinicUserId());
        }
        if (existing.isEmpty() && claims.email() != null && !claims.email().isBlank()) {
            existing = userRepository.findByEmail(claims.email().trim().toLowerCase());
        }
        if (existing.isEmpty() && claims.username() != null && !claims.username().isBlank()) {
            existing = userRepository.findByUsername(claims.username().trim().toLowerCase());
        }

        if (existing.isPresent()) {
            User user = existing.get();
            if (!user.isActive()) {
                throw new AccountDisabledException("Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ quản trị viên");
            }
            return user;
        }

        UUID keycloakId = claims.keycloakUserId();
        if (keycloakId == null && claims.subject() != null && UuidV4.isUuidV4String(claims.subject())) {
            keycloakId = UUID.fromString(claims.subject().trim().toLowerCase());
        }
        if (keycloakId == null) {
            keycloakId = UuidV4.generate();
        }

        RoleName resolvedRole = resolveRoleName(claims);
        Role role = roleRepository.findByName(resolvedRole)
                .orElseThrow(() -> new RoleNotFoundException("Không tìm thấy vai trò: " + resolvedRole));

        String username = (claims.username() != null && !claims.username().isBlank())
                ? claims.username().trim().toLowerCase()
                : "kc_" + keycloakId.toString().substring(0, 8);
        String email = (claims.email() != null && !claims.email().isBlank())
                ? claims.email().trim().toLowerCase()
                : username + "@fclinic.local";
        String fullName = (claims.fullName() != null && !claims.fullName().isBlank())
                ? claims.fullName().trim()
                : username;

        UUID userId = claims.fclinicUserId() != null && UuidV4.isUuidV4(claims.fclinicUserId())
                ? claims.fclinicUserId()
                : UuidV4.generate();

        User synced = User.create(
                userId,
                keycloakId,
                username,
                email,
                null,
                fullName,
                role,
                claims.patientId(),
                claims.doctorId(),
                null
        );
        return userRepository.save(synced);
    }

    private RoleName resolveRoleName(KeycloakTokenClaims claims) {
        if (claims.authorities() != null) {
            for (RoleName candidate : RoleName.values()) {
                if (claims.authorities().contains(candidate.name())) {
                    return candidate;
                }
            }
        }
        return RoleName.ROLE_PATIENT;
    }

    private void validateUniqueness(String username, String email, String phoneNumber) {
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Tên đăng nhập đã tồn tại: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email đã được sử dụng: " + email);
        }
        if (phoneNumber != null && userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new UserAlreadyExistsException("Số điện thoại đã được sử dụng: " + phoneNumber);
        }
    }

    private void compensateKeycloakUser(UUID keycloakId) {
        try {
            keycloakIdentityPort.deleteUser(keycloakId);
            log.info("[Auth] Compensated Keycloak user deletion for keycloakId={}", keycloakId);
        } catch (RuntimeException cleanupEx) {
            log.error("[Auth] Failed to rollback Keycloak user keycloakId={} after local DB error", keycloakId, cleanupEx);
        }
    }
}

