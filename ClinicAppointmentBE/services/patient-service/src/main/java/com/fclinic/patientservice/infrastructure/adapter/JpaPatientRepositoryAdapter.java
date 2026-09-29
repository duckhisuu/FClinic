package com.fclinic.patientservice.infrastructure.adapter;

import com.fclinic.patientservice.application.port.out.PatientRepositoryPort;
import com.fclinic.patientservice.domain.aggregate.Patient;
import com.fclinic.patientservice.domain.repository.PatientRepository;
import com.fclinic.patientservice.infrastructure.entity.JpaPatientEntity;
import com.fclinic.patientservice.infrastructure.persistence.SpringDataJpaPatientRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class JpaPatientRepositoryAdapter implements PatientRepositoryPort, PatientRepository {

    private final SpringDataJpaPatientRepository repository;

    public JpaPatientRepositoryAdapter(SpringDataJpaPatientRepository repository) {
        this.repository = repository;
    }

    @Override
    public Patient save(Patient domain) {
        JpaPatientEntity entity = toEntity(domain);
        JpaPatientEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Patient> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Patient> findByPhoneNumber(String phoneNumber) {
        return repository.findByPhoneNumber(phoneNumber).map(this::toDomain);
    }

    @Override
    public boolean existsByPhoneNumber(String phoneNumber) {
        return repository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    public List<Patient> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public long count() {
        return repository.count();
    }

    private JpaPatientEntity toEntity(Patient d) {
        return new JpaPatientEntity(
                d.getId(),
                d.getFullName(),
                d.getPhoneNumber(),
                d.getEmail(),
                d.getDateOfBirth(),
                d.getGender(),
                d.getAddress(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }

    private Patient toDomain(JpaPatientEntity e) {
        return new Patient(
                e.getId(),
                e.getFullName(),
                e.getPhoneNumber(),
                e.getEmail(),
                e.getDateOfBirth(),
                e.getGender(),
                e.getAddress(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
