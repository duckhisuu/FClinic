package com.fclinic.authservice.infrastructure.persistence;

import com.fclinic.authservice.infrastructure.entity.JpaUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataJpaUserRepository extends JpaRepository<JpaUserEntity, UUID>, JpaSpecificationExecutor<JpaUserEntity> {

    java.util.List<JpaUserEntity> findTop2ByPatientIdAndActiveTrueAndRole_RoleName(UUID patientId, String roleName);

    java.util.List<JpaUserEntity> findTop2ByDoctorIdAndActiveTrueAndRole_RoleName(UUID doctorId, String roleName);

    Optional<JpaUserEntity> findByKeycloakId(UUID keycloakId);

    Optional<JpaUserEntity> findByUsernameIgnoreCase(String username);

    Optional<JpaUserEntity> findByEmailIgnoreCase(String email);

    Optional<JpaUserEntity> findByPhoneNumber(String phoneNumber);

    Optional<JpaUserEntity> findByGoogleId(String googleId);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    @Query("""
            SELECT u FROM JpaUserEntity u
            WHERE u.role.roleName = 'ROLE_PATIENT'
              AND (
                   u.phoneNumber = :query
                   OR LOWER(u.email) = LOWER(:query)
                   OR LOWER(u.username) = LOWER(:query)
              )
            """)
    Optional<JpaUserEntity> lookupPatient(@Param("query") String query);
}

