package com.fclinic.doctorservice.infrastructure.adapter;

import com.fclinic.doctorservice.application.port.out.TimeSlotRepositoryPort;
import com.fclinic.doctorservice.domain.model.TimeSlot;
import com.fclinic.doctorservice.domain.repository.TimeSlotRepository;
import com.fclinic.doctorservice.infrastructure.entity.JpaTimeSlotEntity;
import com.fclinic.doctorservice.infrastructure.mapper.TimeSlotPersistenceMapper;
import com.fclinic.doctorservice.infrastructure.persistence.SpringDataJpaTimeSlotRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaTimeSlotRepositoryAdapter implements TimeSlotRepositoryPort, TimeSlotRepository {

    private final SpringDataJpaTimeSlotRepository repository;

    public JpaTimeSlotRepositoryAdapter(SpringDataJpaTimeSlotRepository repository) {
        this.repository = repository;
    }

    @Override
    public TimeSlot save(TimeSlot domain) {
        JpaTimeSlotEntity entity = TimeSlotPersistenceMapper.toEntity(domain);
        JpaTimeSlotEntity saved = repository.save(entity);
        return TimeSlotPersistenceMapper.toDomain(saved);
    }

    @Override
    public Optional<TimeSlot> findById(UUID id) {
        return repository.findById(id).map(TimeSlotPersistenceMapper::toDomain);
    }

    @Override
    public Optional<TimeSlot> findByIdAndDoctorId(UUID id, UUID doctorId) {
        return repository.findByIdAndDoctorId(id, doctorId).map(TimeSlotPersistenceMapper::toDomain);
    }

    @Override
    public List<TimeSlot> findByDoctorIdAndSlotDateOrderByStartTimeAsc(UUID doctorId, LocalDate date) {
        return repository.findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctorId, date)
                .stream().map(TimeSlotPersistenceMapper::toDomain).toList();
    }

    @Override
    public List<TimeSlot> findByDoctorIdAndSlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(UUID doctorId, LocalDate date) {
        return repository.findByDoctorIdAndSlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(doctorId, date)
                .stream().map(TimeSlotPersistenceMapper::toDomain).toList();
    }

    @Override
    public List<TimeSlot> findByDoctorIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
            UUID doctorId, LocalDate fromDate, LocalDate toDate) {
        return repository.findByDoctorIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(doctorId, fromDate, toDate)
                .stream().map(TimeSlotPersistenceMapper::toDomain).toList();
    }

    @Override
    public long count() {
        return repository.count();
    }
}
