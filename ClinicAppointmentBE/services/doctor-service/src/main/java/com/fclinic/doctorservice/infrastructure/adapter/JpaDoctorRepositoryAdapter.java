package com.fclinic.doctorservice.infrastructure.adapter;

import com.fclinic.doctorservice.application.port.out.DoctorRepositoryPort;
import com.fclinic.doctorservice.domain.aggregate.Doctor;
import com.fclinic.doctorservice.domain.repository.DoctorRepository;
import com.fclinic.doctorservice.infrastructure.entity.JpaDoctorEntity;
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
        JpaDoctorEntity entity = toEntity(domain);
        JpaDoctorEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Doctor> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Doctor> findByActiveTrue() {
        return repository.findByActiveTrue().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Doctor> findBySpecialtyIgnoreCaseAndActiveTrue(String specialty) {
        return repository.findBySpecialtyIgnoreCaseAndActiveTrue(specialty).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Doctor> findByDepartmentIgnoreCaseAndActiveTrue(String department) {
        return repository.findByDepartmentIgnoreCaseAndActiveTrue(department).stream().map(this::toDomain).toList();
    }

    @Override
    public long count() {
        return repository.count();
    }

    private JpaDoctorEntity toEntity(Doctor d) {
        return new JpaDoctorEntity(
                d.getId(),
                d.getName(),
                d.getSpecialty(),
                d.getDepartment(),
                d.getQualification(),
                d.getExperienceYears(),
                d.getConsultationFee(),
                d.getRoomNumber(),
                d.getBio(),
                d.getAvatarUrl(),
                d.isActive(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }

    private Doctor toDomain(JpaDoctorEntity e) {
        return new Doctor(
                e.getId(),
                e.getName(),
                e.getSpecialty(),
                e.getDepartment(),
                e.getQualification(),
                e.getExperienceYears(),
                e.getConsultationFee(),
                e.getRoomNumber(),
                e.getBio(),
                e.getAvatarUrl(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
