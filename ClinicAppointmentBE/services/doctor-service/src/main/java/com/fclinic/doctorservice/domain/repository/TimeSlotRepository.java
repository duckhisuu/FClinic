package com.fclinic.doctorservice.domain.repository;

import com.fclinic.doctorservice.domain.model.TimeSlot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TimeSlotRepository {

    TimeSlot save(TimeSlot timeSlot);

    Optional<TimeSlot> findById(Long id);

    List<TimeSlot> findByDoctorIdAndSlotDateOrderByStartTimeAsc(Long doctorId, LocalDate date);

    List<TimeSlot> findByDoctorIdAndSlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(Long doctorId, LocalDate date);

    long count();
}
