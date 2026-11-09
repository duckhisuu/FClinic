package com.fclinic.doctorservice.application.usecase;

import com.fclinic.doctorservice.application.command.CreateDoctorCommand;
import com.fclinic.doctorservice.application.command.CreateTimeSlotCommand;
import com.fclinic.doctorservice.application.exception.DoctorNotFoundException;
import com.fclinic.doctorservice.application.model.SchedulePeriod;
import com.fclinic.doctorservice.application.port.out.DoctorRepositoryPort;
import com.fclinic.doctorservice.application.port.out.TimeSlotRepositoryPort;
import com.fclinic.doctorservice.application.result.DoctorScheduleView;
import com.fclinic.doctorservice.application.result.DoctorView;
import com.fclinic.doctorservice.application.result.TimeSlotView;
import com.fclinic.doctorservice.domain.aggregate.Doctor;
import com.fclinic.doctorservice.domain.model.TimeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorApplicationServiceTest {

    @Mock
    private DoctorRepositoryPort doctorRepository;

    @Mock
    private TimeSlotRepositoryPort timeSlotRepository;

    private DoctorApplicationService service;

    private final UUID doctorId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID slotId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @BeforeEach
    void setUp() {
        service = new DoctorApplicationService(doctorRepository, timeSlotRepository);
    }

    @Test
    void shouldCreateDoctorSuccessfully() {
        CreateDoctorCommand cmd = new CreateDoctorCommand(
                "BS. Nguyễn Văn A", "Tim Mạch", "Khoa Tim", "Tiến sĩ",
                15, 300000.0, "P.101", "Bio", "http://avatar.png"
        );

        when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DoctorView view = service.createDoctor(cmd);

        assertNotNull(view);
        assertNotNull(view.id());
        assertEquals("BS. Nguyễn Văn A", view.name());
        assertEquals("Tim Mạch", view.specialty());
        assertTrue(view.active());
        verify(doctorRepository).save(any(Doctor.class));
    }

    @Test
    void shouldSeedBookableSlotsAndMatchDoctorIdentity() {
        when(doctorRepository.count()).thenReturn(0L);
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var doctors = org.mockito.ArgumentCaptor.forClass(Doctor.class);
        var slots = org.mockito.ArgumentCaptor.forClass(TimeSlot.class);

        service.initSampleData();

        verify(doctorRepository, times(4)).save(doctors.capture());
        assertEquals(UUID.fromString("33333333-3333-4333-8333-333333333301"),
                doctors.getAllValues().get(0).getId());
        verify(timeSlotRepository, times(30)).save(slots.capture());
        for (TimeSlot slot : slots.getAllValues()) {
            assertEquals(4, slot.getId().version());
            assertTrue(slot.isPublished());
            assertEquals(Duration.ofHours(1), Duration.between(slot.getStartTime(), slot.getEndTime()));
            assertEquals(0, slot.getStartTime().getMinute());
        }
    }

    @Test
    void shouldGetDoctorByIdWhenExists() {
        Doctor doc = Doctor.createNew("BS. A", "Nhi", "Khoa Nhi", "ThS", 10, 250000.0, "P.102", "Bio", null);
        doc.setId(doctorId);

        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doc));

        Optional<DoctorView> result = service.getById(doctorId);

        assertTrue(result.isPresent());
        assertEquals(doctorId, result.get().id());
        assertEquals("BS. A", result.get().name());
    }

    @Test
    void shouldReturnEmptyWhenDoctorNotFound() {
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.empty());

        Optional<DoctorView> result = service.getById(doctorId);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldCreateTimeSlotSuccessfully() {
        Doctor doc = Doctor.createNew("BS. A", "Nhi", "Khoa Nhi", "ThS", 10, 250000.0, "P.102", "Bio", null);
        doc.setId(doctorId);
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doc));

        LocalDate date = LocalDate.now().plusDays(1);
        CreateTimeSlotCommand cmd = new CreateTimeSlotCommand(
                doctorId, date, LocalTime.of(8, 0), LocalTime.of(9, 0)
        );

        when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TimeSlotView view = service.createTimeSlot(cmd);

        assertNotNull(view);
        assertNotNull(view.id());
        assertEquals(doctorId, view.doctorId());
        assertEquals(date, view.slotDate());
        assertEquals(LocalTime.of(8, 0), view.startTime());
        assertEquals(LocalTime.of(9, 0), view.endTime());
        assertFalse(view.isBooked());
        assertTrue(view.published());
    }

    @Test
    void shouldThrowExceptionWhenSlotEndTimeBeforeStartTime() {
        Doctor doc = Doctor.createNew("BS. A", "Nhi", "Khoa Nhi", "ThS", 10, 250000.0, "P.102", "Bio", null);
        doc.setId(doctorId);
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doc));

        CreateTimeSlotCommand cmd = new CreateTimeSlotCommand(
                doctorId, LocalDate.now(), LocalTime.of(9, 0), LocalTime.of(8, 30)
        );

        assertThrows(IllegalArgumentException.class, () -> service.createTimeSlot(cmd));
    }

    @Test
    void shouldThrowExceptionWhenCreatingSlotForNonExistentDoctor() {
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.empty());

        CreateTimeSlotCommand cmd = new CreateTimeSlotCommand(
                doctorId, LocalDate.now(), LocalTime.of(8, 0), LocalTime.of(9, 0)
        );

        assertThrows(DoctorNotFoundException.class, () -> service.createTimeSlot(cmd));
    }

    @Test
    void shouldThrowExceptionWhenSlotDurationIsNot60MinutesOrNotWholeHour() {
        Doctor doc = Doctor.createNew("BS. A", "Nhi", "Khoa Nhi", "ThS", 10, 250000.0, "P.102", "Bio", null);
        doc.setId(doctorId);
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doc));

        // 30-minute slot: 08:00 - 08:30
        CreateTimeSlotCommand cmd30Min = new CreateTimeSlotCommand(
                doctorId, LocalDate.now(), LocalTime.of(8, 0), LocalTime.of(8, 30)
        );
        assertThrows(IllegalArgumentException.class, () -> service.createTimeSlot(cmd30Min));

        // Not starting on the hour: 08:30 - 09:30
        CreateTimeSlotCommand cmdNotWholeHour = new CreateTimeSlotCommand(
                doctorId, LocalDate.now(), LocalTime.of(8, 30), LocalTime.of(9, 30)
        );
        assertThrows(IllegalArgumentException.class, () -> service.createTimeSlot(cmdNotWholeHour));
    }

    @Test
    void shouldGetPublishedSlotSuccessfully() {
        Doctor doc = Doctor.createNew("BS. A", "Nhi", "Khoa Nhi", "ThS", 10, 250000.0, "P.102", "Bio", null);
        doc.setId(doctorId);
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doc));

        TimeSlot slot = new TimeSlot(slotId, doctorId, LocalDate.now(), LocalTime.of(8, 0), LocalTime.of(9, 0), false, true);
        when(timeSlotRepository.findByIdAndDoctorId(slotId, doctorId)).thenReturn(Optional.of(slot));

        Optional<TimeSlotView> result = service.getPublishedSlot(doctorId, slotId);

        assertTrue(result.isPresent());
        assertEquals(slotId, result.get().id());
        assertEquals(doctorId, result.get().doctorId());
        assertTrue(result.get().published());
    }

    @Test
    void shouldGetScheduleSuccessfully() {
        Doctor doc = Doctor.createNew("BS. A", "Nhi", "Khoa Nhi", "ThS", 10, 250000.0, "P.102", "Bio", null);
        doc.setId(doctorId);
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doc));

        LocalDate anchor = LocalDate.of(2026, 11, 2);
        TimeSlot slot = new TimeSlot(slotId, doctorId, anchor, LocalTime.of(8, 0), LocalTime.of(9, 0), false, true);
        when(timeSlotRepository.findByDoctorIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(any(), any(), any()))
                .thenReturn(List.of(slot));

        DoctorScheduleView schedule = service.getSchedule(doctorId, SchedulePeriod.WEEK, anchor);

        assertNotNull(schedule);
        assertEquals(doctorId, schedule.doctorId());
        assertEquals(1, schedule.slots().size());
        assertEquals(slotId, schedule.slots().get(0).id());
    }
}
