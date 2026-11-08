package com.fclinic.authservice.application.service;

import com.fclinic.authservice.application.command.CreateUserCommand;
import com.fclinic.authservice.application.command.UpdateUserCommand;
import com.fclinic.authservice.application.port.in.ManageUserUseCase;
import com.fclinic.authservice.application.port.out.KeycloakIdentityPort;
import com.fclinic.authservice.application.port.out.RoleRepositoryPort;
import com.fclinic.authservice.application.port.out.UserRepositoryPort;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.exception.RoleNotFoundException;
import com.fclinic.authservice.domain.exception.UserAlreadyExistsException;
import com.fclinic.authservice.domain.exception.UserNotFoundException;
import com.fclinic.authservice.domain.util.UuidV4;
import com.fclinic.authservice.domain.valueobject.Role;
import com.fclinic.authservice.domain.valueobject.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminUserService implements ManageUserUseCase {

    private final UserRepositoryPort userRepository;
    private final RoleRepositoryPort roleRepository;
    private final KeycloakIdentityPort keycloakIdentityPort;
    private final UserProvisioningSupport provisioningSupport;

    public AdminUserService(
            UserRepositoryPort userRepository,
            RoleRepositoryPort roleRepository,
            KeycloakIdentityPort keycloakIdentityPort,
            UserProvisioningSupport provisioningSupport
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.keycloakIdentityPort = keycloakIdentityPort;
        this.provisioningSupport = provisioningSupport;
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> searchUsers(String keyword, String role, Boolean active) {
        RoleName roleName = (role != null && !role.isBlank()) ? RoleName.fromString(role) : null;
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return userRepository.search(cleanKeyword, roleName, active);
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(UUID id) {
        UUID validId = UuidV4.requireValid(id, "userId");
        return userRepository.findById(validId)
                .orElseThrow(() -> new UserNotFoundException("Không tìm thấy người dùng với ID: " + validId));
    }

    @Override
    @Transactional
    public User createUser(CreateUserCommand command) {
        RoleName roleName = RoleName.fromString(command.role());
        return provisioningSupport.provisionNewUser(
                command.username(),
                command.password(),
                command.fullName(),
                command.email(),
                command.phoneNumber(),
                roleName,
                command.patientId(),
                command.doctorId(),
                null
        );
    }

    @Override
    @Transactional
    public User updateUser(UUID id, UpdateUserCommand command) {
        User user = getUserById(id);

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

        boolean roleChanged = false;
        if (command.role() != null && !command.role().isBlank()) {
            RoleName targetRoleName = RoleName.fromString(command.role());
            if (targetRoleName != user.getRoleName()) {
                Role newRole = roleRepository.findByName(targetRoleName)
                        .orElseThrow(() -> new RoleNotFoundException("Không tìm thấy vai trò: " + targetRoleName));
                user.changeRole(newRole);
                roleChanged = true;
            }
        }

        if (command.patientId() != null) {
            user.linkPatientId(command.patientId());
        }
        if (command.doctorId() != null) {
            user.linkDoctorId(command.doctorId());
        }

        Boolean statusChanged = null;
        if (command.active() != null && command.active() != user.isActive()) {
            user.setActiveStatus(command.active());
            statusChanged = command.active();
        }

        User saved = userRepository.save(user);
        keycloakIdentityPort.updateUserProfile(
                saved.getKeycloakId(),
                saved.getEmail(),
                saved.getFullName(),
                saved.getPhoneNumber(),
                saved.getPatientId(),
                saved.getDoctorId()
        );
        if (roleChanged) {
            keycloakIdentityPort.replaceRealmRole(saved.getKeycloakId(), saved.getRoleName());
        }
        if (statusChanged != null) {
            keycloakIdentityPort.setUserEnabled(saved.getKeycloakId(), statusChanged);
            if (!statusChanged) {
                keycloakIdentityPort.revokeAllUserSessions(saved.getKeycloakId());
            }
        }
        return saved;
    }

    @Override
    @Transactional
    public User toggleUserStatus(UUID id, boolean active) {
        User user = getUserById(id);
        user.setActiveStatus(active);
        User saved = userRepository.save(user);

        keycloakIdentityPort.setUserEnabled(saved.getKeycloakId(), active);
        if (!active) {
            keycloakIdentityPort.revokeAllUserSessions(saved.getKeycloakId());
        }
        return saved;
    }

    @Override
    @Transactional
    public User updateUserRole(UUID id, String role) {
        User user = getUserById(id);
        RoleName targetRoleName = RoleName.fromString(role);
        Role targetRole = roleRepository.findByName(targetRoleName)
                .orElseThrow(() -> new RoleNotFoundException("Không tìm thấy vai trò: " + targetRoleName));

        user.changeRole(targetRole);
        User saved = userRepository.save(user);
        keycloakIdentityPort.replaceRealmRole(saved.getKeycloakId(), targetRoleName);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }
}

