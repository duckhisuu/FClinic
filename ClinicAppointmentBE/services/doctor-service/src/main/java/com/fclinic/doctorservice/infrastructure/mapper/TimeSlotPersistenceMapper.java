package com.fclinic.doctorservice.infrastructure.mapper;

import com.fclinic.doctorservice.domain.model.TimeSlot;
import com.fclinic.doctorservice.infrastructure.entity.JpaTimeSlotEntity;

public final class TimeSlotPersistenceMapper {

    private TimeSlotPersistenceMapper() {
    }

    public static JpaTimeSlotEntity toEntity(TimeSlot slot) {
        return new JpaTimeSlotEntity(slot.getId(), slot.getDoctorId(), slot.getSlotDate(), slot.getStartTime(),
                slot.getEndTime(), slot.isBooked());
    }

    public static TimeSlot toDomain(JpaTimeSlotEntity entity) {
        return new TimeSlot(entity.getId(), entity.getDoctorId(), entity.getSlotDate(), entity.getStartTime(),
                entity.getEndTime(), entity.isBooked());
    }
}
