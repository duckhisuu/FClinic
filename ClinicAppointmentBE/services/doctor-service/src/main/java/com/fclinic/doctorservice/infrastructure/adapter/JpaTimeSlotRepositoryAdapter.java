package com.fclinic.doctorservice.infrastructure.adapter;

import com.fclinic.doctorservice.application.port.out.TimeSlotRepositoryPort;
import com.fclinic.doctorservice.domain.model.TimeSlot;
import com.fclinic.doctorservice.domain.repository.TimeSlotRepository;
import com.fclinic.doctorservice.infrastructure.entity.JpaTimeSlotEntity;
import com.fclinic.doctorservice.infrastructure.mapper.TimeSlotPersistenceMapper;
import com.fclinic.doctorservice.infrastructure.persistence.SpringDataJpaTimeSlotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.fclinic.doctorservice.infrastructure.entity.JpaDoctorEntity;
import com.fclinic.doctorservice.application.exception.DoctorNotFoundException;
import com.fclinic.doctorservice.application.exception.SlotOverlapException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaTimeSlotRepositoryAdapter implements TimeSlotRepositoryPort, TimeSlotRepository {

    private final SpringDataJpaTimeSlotRepository repository;
    private final EntityManager entityManager;

    public JpaTimeSlotRepositoryAdapter(SpringDataJpaTimeSlotRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public TimeSlot save(TimeSlot domain) {
        // Lock the parent, including when no slots exist yet. All writers for this
        // doctor serialize across application instances until commit/rollback.
        if (entityManager.find(JpaDoctorEntity.class, domain.getDoctorId(), LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new DoctorNotFoundException("Không tìm thấy bác sĩ với ID: " + domain.getDoctorId());
        }
        if (repository.findByDoctorIdAndSlotDateOrderByStartTimeAsc(domain.getDoctorId(), domain.getSlotDate())
                .stream().anyMatch(existing -> !existing.getId().equals(domain.getId())
                    && existing.getStartTime().isBefore(domain.getEndTime())
                    && domain.getStartTime().isBefore(existing.getEndTime()))) {
            throw new SlotOverlapException();
        }
        JpaTimeSlotEntity entity = TimeSlotPersistenceMapper.toEntity(domain);
        JpaTimeSlotEntity saved = repository.saveAndFlush(entity);
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
