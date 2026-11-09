package com.fclinic.doctorservice.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DoctorScheduleResponse(
        UUID doctorId,
        String period,
        LocalDate fromDate,
        LocalDate toDate,
        List<ScheduleDayResponse> days
) {
    public record ScheduleDayResponse(LocalDate date, List<TimeSlotResponse> slots) {
    }
}
