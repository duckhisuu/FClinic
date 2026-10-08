package com.fclinic.doctorservice.api.dto;

import java.time.LocalDate;
import java.util.List;

public record DoctorScheduleResponse(
        Long doctorId,
        String period,
        LocalDate fromDate,
        LocalDate toDate,
        List<ScheduleDayResponse> days
) {
    public record ScheduleDayResponse(LocalDate date, List<TimeSlotResponse> slots) {
    }
}
