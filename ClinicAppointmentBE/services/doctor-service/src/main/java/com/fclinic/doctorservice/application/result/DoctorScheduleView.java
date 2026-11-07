package com.fclinic.doctorservice.application.result;

import com.fclinic.doctorservice.application.model.SchedulePeriod;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DoctorScheduleView(
        UUID doctorId,
        SchedulePeriod period,
        LocalDate fromDate,
        LocalDate toDate,
        List<TimeSlotView> slots
) {
}
