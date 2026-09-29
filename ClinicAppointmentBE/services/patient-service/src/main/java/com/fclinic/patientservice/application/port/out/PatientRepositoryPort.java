package com.fclinic.patientservice.application.port.out;

import com.fclinic.patientservice.domain.aggregate.Patient;
import java.util.List;
import java.util.Optional;

public interface PatientRepositoryPort {
    Patient save(Patient patient);
    Optional<Patient> findById(Long id);
    Optional<Patient> findByPhoneNumber(String phoneNumber);
    boolean existsByPhoneNumber(String phoneNumber);
    List<Patient> findAll();
    long count();
}
