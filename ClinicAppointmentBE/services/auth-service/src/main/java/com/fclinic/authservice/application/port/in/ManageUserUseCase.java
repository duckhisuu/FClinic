package com.fclinic.authservice.application.port.in;

import com.fclinic.authservice.application.command.CreateUserCommand;
import com.fclinic.authservice.application.command.UpdateUserCommand;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.valueobject.Role;

import java.util.List;
import java.util.UUID;

public interface ManageUserUseCase {

    List<User> searchUsers(String keyword, String role, Boolean active);

    User getUserById(UUID id);

    User createUser(CreateUserCommand command);

    User updateUser(UUID id, UpdateUserCommand command);

    User toggleUserStatus(UUID id, boolean active);

    User updateUserRole(UUID id, String role);

    List<Role> getAllRoles();
}

