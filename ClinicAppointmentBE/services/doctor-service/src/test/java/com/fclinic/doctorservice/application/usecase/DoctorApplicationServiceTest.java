package com.fclinic.doctorservice.application.usecase;

import com.fclinic.doctorservice.application.model.SchedulePeriod;
import com.fclinic.doctorservice.application.port.out.DoctorRepositoryPort;
import com.fclinic.doctorservice.application.port.out.TimeSlotRepositoryPort;
import com.fclinic.doctorservice.domain.aggregate.Doctor;
import com.fclinic.doctorservice.domain.model.TimeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorApplicationServiceTest {

    @Mock
    private DoctorRepositoryPort doctorRepository;
    @Mock
    private TimeSlotRepositoryPort timeSlotRepository;

    private DoctorApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DoctorApplicationService(doctorRepository, timeSlotRepository);
        when(doctorRepository.findById(7L)).thenReturn(Optional.of(doctor(7L)));
    }

    @Test
    void getsMondayToSundayScheduleForWeek() {
        LocalDate anchor = LocalDate.of(2026, 10, 8);
        LocalDate monday = LocalDate.of(2026, 10, 5);
        LocalDate sunday = LocalDate.of(2026, 10, 11);
        TimeSlot slot = TimeSlot.createNew(7L, anchor, LocalTime.of(8, 0), LocalTime.of(8, 30));
        when(timeSlotRepository.findByDoctorIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(7L, monday, sunday))
                .thenReturn(List.of(slot));

        var schedule = service.getSchedule(7L, SchedulePeriod.WEEK, anchor);

        assertThat(schedule.fromDate()).isEqualTo(monday);
        assertThat(schedule.toDate()).isEqualTo(sunday);
        assertThat(schedule.slots()).hasSize(1);
        verify(timeSlotRepository)
                .findByDoctorIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(7L, monday, sunday);
    }

    @Test
    void getsFirstToLastDayScheduleForMonth() {
        LocalDate anchor = LocalDate.of(2026, 2, 18);
        LocalDate firstDay = LocalDate.of(2026, 2, 1);
        LocalDate lastDay = LocalDate.of(2026, 2, 28);
        when(timeSlotRepository.findByDoctorIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(7L, firstDay, lastDay))
                .thenReturn(List.of());

        var schedule = service.getSchedule(7L, SchedulePeriod.MONTH, anchor);

        assertThat(schedule.fromDate()).isEqualTo(firstDay);
        assertThat(schedule.toDate()).isEqualTo(lastDay);
        assertThat(schedule.slots()).isEmpty();
    }

    private Doctor doctor(Long id) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new Doctor(id, "Doctor", "Cardiology", "Internal Medicine", null,
                10, 300000D, "101", null, null, true, now, now);
    }
}
