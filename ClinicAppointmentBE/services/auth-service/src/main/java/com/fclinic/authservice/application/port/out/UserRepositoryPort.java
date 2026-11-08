package com.fclinic.authservice.application.port.out;

import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.valueobject.RoleName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByKeycloakId(UUID keycloakId);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    List<User> search(String keyword, RoleName roleName, Boolean active);

    Optional<User> lookupPatient(String query);
}

