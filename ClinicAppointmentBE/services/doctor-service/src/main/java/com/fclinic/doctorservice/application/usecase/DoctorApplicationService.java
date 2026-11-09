package com.fclinic.doctorservice.application.usecase;

import com.fclinic.doctorservice.application.command.CreateDoctorCommand;
import com.fclinic.doctorservice.application.command.CreateTimeSlotCommand;
import com.fclinic.doctorservice.application.exception.DoctorNotFoundException;
import com.fclinic.doctorservice.application.mapper.DoctorApplicationMapper;
import com.fclinic.doctorservice.application.model.SchedulePeriod;
import com.fclinic.doctorservice.application.port.in.*;
import com.fclinic.doctorservice.application.port.out.DoctorRepositoryPort;
import com.fclinic.doctorservice.application.port.out.TimeSlotRepositoryPort;
import com.fclinic.doctorservice.application.result.DoctorScheduleView;
import com.fclinic.doctorservice.application.result.DoctorView;
import com.fclinic.doctorservice.application.result.TimeSlotView;
import com.fclinic.doctorservice.domain.aggregate.Doctor;
import com.fclinic.doctorservice.domain.model.TimeSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DoctorApplicationService implements
        GetDoctorUseCase,
        ListDoctorsUseCase,
        CreateDoctorUseCase,
        ListTimeSlotsUseCase,
        CreateTimeSlotUseCase,
        GetDoctorScheduleUseCase,
        GetPublishedSlotUseCase {

    private static final Logger log = LoggerFactory.getLogger(DoctorApplicationService.class);
    private final DoctorRepositoryPort doctorRepository;
    private final TimeSlotRepositoryPort timeSlotRepository;

    public DoctorApplicationService(DoctorRepositoryPort doctorRepository, TimeSlotRepositoryPort timeSlotRepository) {
        this.doctorRepository = doctorRepository;
        this.timeSlotRepository = timeSlotRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DoctorView> getById(UUID id) {
        return doctorRepository.findById(id).map(DoctorApplicationMapper::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorView> listDoctors(String specialty, String department) {
        if (specialty != null && !specialty.isBlank()) {
            return doctorRepository.findBySpecialtyIgnoreCaseAndActiveTrue(specialty)
                    .stream().map(DoctorApplicationMapper::toView).toList();
        }
        if (department != null && !department.isBlank()) {
            return doctorRepository.findByDepartmentIgnoreCaseAndActiveTrue(department)
                    .stream().map(DoctorApplicationMapper::toView).toList();
        }
        return doctorRepository.findByActiveTrue().stream().map(DoctorApplicationMapper::toView).toList();
    }

    @Override
    @Transactional
    public DoctorView createDoctor(CreateDoctorCommand command) {
        Doctor doctor = Doctor.createNew(
                command.name(),
                command.specialty(),
                command.department(),
                command.qualification(),
                command.experienceYears(),
                command.consultationFee(),
                command.roomNumber(),
                command.bio(),
                command.avatarUrl()
        );
        Doctor saved = doctorRepository.save(doctor);
        log.info("[DoctorApplicationService] Created doctor id={} name={}", saved.getId(), saved.getName());
        return DoctorApplicationMapper.toView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeSlotView> listSlots(UUID doctorId, LocalDate date) {
        ensureDoctorExists(doctorId);
        if (date != null) {
            return timeSlotRepository.findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctorId, date)
                    .stream().map(DoctorApplicationMapper::toView).toList();
        }
        return timeSlotRepository.findByDoctorIdAndSlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(doctorId, LocalDate.now())
                .stream().map(DoctorApplicationMapper::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DoctorScheduleView getSchedule(UUID doctorId, SchedulePeriod period, LocalDate anchorDate) {
        ensureDoctorExists(doctorId);
        LocalDate anchor = anchorDate == null ? LocalDate.now() : anchorDate;
        SchedulePeriod requestedPeriod = period == null ? SchedulePeriod.WEEK : period;
        LocalDate fromDate;
        LocalDate toDate;

        if (requestedPeriod == SchedulePeriod.MONTH) {
            fromDate = anchor.withDayOfMonth(1);
            toDate = anchor.withDayOfMonth(anchor.lengthOfMonth());
        } else {
            fromDate = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            toDate = fromDate.plusDays(6);
        }

        List<TimeSlotView> slots = timeSlotRepository
                .findByDoctorIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(doctorId, fromDate, toDate)
                .stream().map(DoctorApplicationMapper::toView).toList();
        return new DoctorScheduleView(doctorId, requestedPeriod, fromDate, toDate, slots);
    }

    @Override
    @Transactional
    public TimeSlotView createTimeSlot(CreateTimeSlotCommand command) {
        ensureDoctorExists(command.doctorId());
        if (command.startTime().getMinute() != 0 || command.startTime().getSecond() != 0 || command.startTime().getNano() != 0
                || !java.time.Duration.between(command.startTime(), command.endTime()).equals(java.time.Duration.ofHours(1))) {
            throw new IllegalArgumentException("Khung giờ phải bắt đầu đúng giờ và kéo dài 60 phút");
        }
        TimeSlot slot = TimeSlot.createNew(
                command.doctorId(),
                command.slotDate(),
                command.startTime(),
                command.endTime()
        );
        TimeSlot saved = timeSlotRepository.save(slot);
        log.info("[DoctorApplicationService] Created slot id={} for doctor={}", saved.getId(), command.doctorId());
        return DoctorApplicationMapper.toView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TimeSlotView> getPublishedSlot(UUID doctorId, UUID slotId) {
        ensureDoctorExists(doctorId);
        return timeSlotRepository.findByIdAndDoctorId(slotId, doctorId)
                .map(DoctorApplicationMapper::toView);
    }

    private void ensureDoctorExists(UUID doctorId) {
        if (doctorRepository.findById(doctorId).isEmpty()) {
            throw new DoctorNotFoundException("Không tìm thấy bác sĩ với ID: " + doctorId);
        }
    }
}
