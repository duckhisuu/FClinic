package com.fclinic.doctorservice.application.port.in;

import com.fclinic.doctorservice.application.model.SchedulePeriod;
import com.fclinic.doctorservice.application.result.DoctorScheduleView;

import java.time.LocalDate;

public interface GetDoctorScheduleUseCase {
    DoctorScheduleView getSchedule(Long doctorId, SchedulePeriod period, LocalDate anchorDate);
}
