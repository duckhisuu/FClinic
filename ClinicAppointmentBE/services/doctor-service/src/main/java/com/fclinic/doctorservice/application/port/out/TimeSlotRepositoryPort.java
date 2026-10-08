package com.fclinic.doctorservice.application.port.out;

import com.fclinic.doctorservice.domain.model.TimeSlot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TimeSlotRepositoryPort {
    TimeSlot save(TimeSlot timeSlot);
    Optional<TimeSlot> findById(Long id);
    List<TimeSlot> findByDoctorIdAndSlotDateOrderByStartTimeAsc(Long doctorId, LocalDate date);
    List<TimeSlot> findByDoctorIdAndSlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(Long doctorId, LocalDate date);
    List<TimeSlot> findByDoctorIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
            Long doctorId, LocalDate fromDate, LocalDate toDate);
    long count();
}
