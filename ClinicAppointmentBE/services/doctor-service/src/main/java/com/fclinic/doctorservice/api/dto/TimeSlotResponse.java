package com.fclinic.doctorservice.api.dto;

import com.fclinic.doctorservice.application.result.TimeSlotView;

import java.time.LocalDate;
import java.time.LocalTime;

public record TimeSlotResponse(
        Long id,
        Long doctorId,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        boolean isBooked
) {
    public static TimeSlotResponse from(TimeSlotView view) {
        return new TimeSlotResponse(
                view.id(),
                view.doctorId(),
                view.slotDate(),
                view.startTime(),
                view.endTime(),
                view.isBooked()
        );
    }
}
