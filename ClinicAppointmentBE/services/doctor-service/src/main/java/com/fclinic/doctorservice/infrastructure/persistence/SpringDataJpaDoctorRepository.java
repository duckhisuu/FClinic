package com.fclinic.doctorservice.infrastructure.persistence;

import com.fclinic.doctorservice.infrastructure.entity.JpaDoctorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataJpaDoctorRepository extends JpaRepository<JpaDoctorEntity, Long> {
    List<JpaDoctorEntity> findByActiveTrue();
    List<JpaDoctorEntity> findBySpecialtyIgnoreCaseAndActiveTrue(String specialty);
    List<JpaDoctorEntity> findByDepartmentIgnoreCaseAndActiveTrue(String department);
}
