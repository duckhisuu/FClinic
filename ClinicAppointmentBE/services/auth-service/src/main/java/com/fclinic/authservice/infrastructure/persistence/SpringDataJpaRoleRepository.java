package com.fclinic.authservice.infrastructure.persistence;

import com.fclinic.authservice.infrastructure.entity.JpaRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataJpaRoleRepository extends JpaRepository<JpaRoleEntity, UUID> {

    Optional<JpaRoleEntity> findByRoleName(String roleName);
}

