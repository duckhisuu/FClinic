package com.fclinic.doctorservice.application.port.out;

import com.fclinic.doctorservice.domain.model.TimeSlot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeSlotRepositoryPort {
    TimeSlot save(TimeSlot timeSlot);
    Optional<TimeSlot> findById(UUID id);
    Optional<TimeSlot> findByIdAndDoctorId(UUID id, UUID doctorId);
    List<TimeSlot> findByDoctorIdAndSlotDateOrderByStartTimeAsc(UUID doctorId, LocalDate date);
    List<TimeSlot> findByDoctorIdAndSlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(UUID doctorId, LocalDate date);
    List<TimeSlot> findByDoctorIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
            UUID doctorId, LocalDate fromDate, LocalDate toDate);
    long count();
}
