package com.fclinic.doctorservice.infrastructure.persistence;

import com.fclinic.doctorservice.infrastructure.entity.JpaTimeSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataJpaTimeSlotRepository extends JpaRepository<JpaTimeSlotEntity, UUID> {
    List<JpaTimeSlotEntity> findByDoctorIdAndSlotDateOrderByStartTimeAsc(UUID doctorId, LocalDate slotDate);
    List<JpaTimeSlotEntity> findByDoctorIdAndSlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(UUID doctorId, LocalDate slotDate);
    List<JpaTimeSlotEntity> findByDoctorIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
            UUID doctorId, LocalDate fromDate, LocalDate toDate);
    Optional<JpaTimeSlotEntity> findByIdAndDoctorId(UUID id, UUID doctorId);
}
