package com.fclinic.doctorservice.application.port.in;

import com.fclinic.doctorservice.application.model.SchedulePeriod;
import com.fclinic.doctorservice.application.result.DoctorScheduleView;

import java.time.LocalDate;
import java.util.UUID;

public interface GetDoctorScheduleUseCase {
    DoctorScheduleView getSchedule(UUID doctorId, SchedulePeriod period, LocalDate anchorDate);
}
