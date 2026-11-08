package com.fclinic.authservice.infrastructure.persistence;

import com.fclinic.authservice.infrastructure.entity.JpaPasswordResetOtpEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataJpaPasswordResetOtpRepository extends JpaRepository<JpaPasswordResetOtpEntity, UUID> {

    Optional<JpaPasswordResetOtpEntity> findTopByEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}

