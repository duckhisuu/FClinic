package com.fclinic.doctorservice.infrastructure.adapter;

import com.fclinic.doctorservice.application.port.out.TimeSlotRepositoryPort;
import com.fclinic.doctorservice.domain.model.TimeSlot;
import com.fclinic.doctorservice.domain.repository.TimeSlotRepository;
import com.fclinic.doctorservice.infrastructure.entity.JpaTimeSlotEntity;
import com.fclinic.doctorservice.infrastructure.persistence.SpringDataJpaTimeSlotRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class JpaTimeSlotRepositoryAdapter implements TimeSlotRepositoryPort, TimeSlotRepository {

    private final SpringDataJpaTimeSlotRepository repository;

    public JpaTimeSlotRepositoryAdapter(SpringDataJpaTimeSlotRepository repository) {
        this.repository = repository;
    }

    @Override
    public TimeSlot save(TimeSlot domain) {
        JpaTimeSlotEntity entity = new JpaTimeSlotEntity(
                domain.getId(),
                domain.getDoctorId(),
                domain.getSlotDate(),
                domain.getStartTime(),
                domain.getEndTime(),
                domain.isBooked()
        );
        JpaTimeSlotEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<TimeSlot> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<TimeSlot> findByDoctorIdAndSlotDateOrderByStartTimeAsc(Long doctorId, LocalDate date) {
        return repository.findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctorId, date)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<TimeSlot> findByDoctorIdAndSlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(Long doctorId, LocalDate date) {
        return repository.findByDoctorIdAndSlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(doctorId, date)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long count() {
        return repository.count();
    }

    private TimeSlot toDomain(JpaTimeSlotEntity e) {
        return new TimeSlot(
                e.getId(),
                e.getDoctorId(),
                e.getSlotDate(),
                e.getStartTime(),
                e.getEndTime(),
                e.isBooked()
        );
    }
}
