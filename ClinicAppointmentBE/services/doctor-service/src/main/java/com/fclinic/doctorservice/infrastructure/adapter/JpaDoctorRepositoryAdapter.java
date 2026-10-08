package com.fclinic.doctorservice.infrastructure.adapter;

import com.fclinic.doctorservice.application.port.out.DoctorRepositoryPort;
import com.fclinic.doctorservice.domain.aggregate.Doctor;
import com.fclinic.doctorservice.domain.repository.DoctorRepository;
import com.fclinic.doctorservice.infrastructure.entity.JpaDoctorEntity;
import com.fclinic.doctorservice.infrastructure.mapper.DoctorPersistenceMapper;
import com.fclinic.doctorservice.infrastructure.persistence.SpringDataJpaDoctorRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class JpaDoctorRepositoryAdapter implements DoctorRepositoryPort, DoctorRepository {

    private final SpringDataJpaDoctorRepository repository;

    public JpaDoctorRepositoryAdapter(SpringDataJpaDoctorRepository repository) {
        this.repository = repository;
    }

    @Override
    public Doctor save(Doctor domain) {
        JpaDoctorEntity entity = DoctorPersistenceMapper.toEntity(domain);
        JpaDoctorEntity saved = repository.save(entity);
        return DoctorPersistenceMapper.toDomain(saved);
    }

    @Override
    public Optional<Doctor> findById(Long id) {
        return repository.findById(id).map(DoctorPersistenceMapper::toDomain);
    }

    @Override
    public List<Doctor> findByActiveTrue() {
        return repository.findByActiveTrue().stream().map(DoctorPersistenceMapper::toDomain).toList();
    }

    @Override
    public List<Doctor> findBySpecialtyIgnoreCaseAndActiveTrue(String specialty) {
        return repository.findBySpecialtyIgnoreCaseAndActiveTrue(specialty).stream().map(DoctorPersistenceMapper::toDomain).toList();
    }

    @Override
    public List<Doctor> findByDepartmentIgnoreCaseAndActiveTrue(String department) {
        return repository.findByDepartmentIgnoreCaseAndActiveTrue(department).stream().map(DoctorPersistenceMapper::toDomain).toList();
    }

    @Override
    public long count() {
        return repository.count();
    }

}
