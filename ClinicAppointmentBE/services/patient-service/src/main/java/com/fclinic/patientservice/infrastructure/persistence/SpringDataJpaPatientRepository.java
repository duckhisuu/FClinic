package com.fclinic.patientservice.infrastructure.persistence;

import com.fclinic.patientservice.infrastructure.entity.JpaPatientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataJpaPatientRepository extends JpaRepository<JpaPatientEntity, Long> {
    Optional<JpaPatientEntity> findByPhoneNumber(String phoneNumber);
    boolean existsByPhoneNumber(String phoneNumber);
}
