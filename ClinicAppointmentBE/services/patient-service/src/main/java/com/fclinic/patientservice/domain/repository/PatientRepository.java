package com.fclinic.patientservice.domain.repository;

import com.fclinic.patientservice.domain.aggregate.Patient;

import java.util.List;
import java.util.Optional;

public interface PatientRepository {

    Patient save(Patient patient);

    Optional<Patient> findById(Long id);

    Optional<Patient> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    List<Patient> findAll();

    long count();
}
