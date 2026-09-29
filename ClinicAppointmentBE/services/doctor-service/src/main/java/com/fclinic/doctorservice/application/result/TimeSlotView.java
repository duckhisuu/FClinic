package com.fclinic.doctorservice.application.result;

import com.fclinic.doctorservice.domain.model.TimeSlot;

import java.time.LocalDate;
import java.time.LocalTime;

public record TimeSlotView(
        Long id,
        Long doctorId,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        boolean isBooked
) {
    public static TimeSlotView from(TimeSlot domain) {
        return new TimeSlotView(
                domain.getId(),
                domain.getDoctorId(),
                domain.getSlotDate(),
                domain.getStartTime(),
                domain.getEndTime(),
                domain.isBooked()
        );
    }
}
