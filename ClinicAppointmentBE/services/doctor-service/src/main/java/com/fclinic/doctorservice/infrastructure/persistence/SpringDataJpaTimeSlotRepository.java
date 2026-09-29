package com.fclinic.doctorservice.infrastructure.persistence;

import com.fclinic.doctorservice.infrastructure.entity.JpaTimeSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SpringDataJpaTimeSlotRepository extends JpaRepository<JpaTimeSlotEntity, Long> {
    List<JpaTimeSlotEntity> findByDoctorIdAndSlotDateOrderByStartTimeAsc(Long doctorId, LocalDate slotDate);
    List<JpaTimeSlotEntity> findByDoctorIdAndSlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(Long doctorId, LocalDate slotDate);
}
