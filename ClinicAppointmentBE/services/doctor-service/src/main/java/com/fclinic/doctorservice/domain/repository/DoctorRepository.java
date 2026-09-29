package com.fclinic.doctorservice.domain.repository;

import com.fclinic.doctorservice.domain.aggregate.Doctor;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository {

    Doctor save(Doctor doctor);

    Optional<Doctor> findById(Long id);

    List<Doctor> findByActiveTrue();

    List<Doctor> findBySpecialtyIgnoreCaseAndActiveTrue(String specialty);

    List<Doctor> findByDepartmentIgnoreCaseAndActiveTrue(String department);

    long count();
}
