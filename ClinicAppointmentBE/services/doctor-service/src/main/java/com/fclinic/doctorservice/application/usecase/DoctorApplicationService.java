package com.fclinic.doctorservice.application.usecase;

import com.fclinic.doctorservice.application.command.CreateDoctorCommand;
import com.fclinic.doctorservice.application.command.CreateTimeSlotCommand;
import com.fclinic.doctorservice.application.exception.DoctorNotFoundException;
import com.fclinic.doctorservice.application.port.in.*;
import com.fclinic.doctorservice.application.port.out.DoctorRepositoryPort;
import com.fclinic.doctorservice.application.port.out.TimeSlotRepositoryPort;
import com.fclinic.doctorservice.application.result.DoctorView;
import com.fclinic.doctorservice.application.result.TimeSlotView;
import com.fclinic.doctorservice.domain.aggregate.Doctor;
import com.fclinic.doctorservice.domain.model.TimeSlot;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class DoctorApplicationService implements
        GetDoctorUseCase,
        ListDoctorsUseCase,
        CreateDoctorUseCase,
        ListTimeSlotsUseCase,
        CreateTimeSlotUseCase {

    private static final Logger log = LoggerFactory.getLogger(DoctorApplicationService.class);

    private final DoctorRepositoryPort doctorRepository;
    private final TimeSlotRepositoryPort timeSlotRepository;

    public DoctorApplicationService(DoctorRepositoryPort doctorRepository, TimeSlotRepositoryPort timeSlotRepository) {
        this.doctorRepository = doctorRepository;
        this.timeSlotRepository = timeSlotRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DoctorView> getById(Long id) {
        return doctorRepository.findById(id).map(DoctorView::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorView> listDoctors(String specialty, String department) {
        if (specialty != null && !specialty.isBlank()) {
            return doctorRepository.findBySpecialtyIgnoreCaseAndActiveTrue(specialty)
                    .stream().map(DoctorView::from).toList();
        }
        if (department != null && !department.isBlank()) {
            return doctorRepository.findByDepartmentIgnoreCaseAndActiveTrue(department)
                    .stream().map(DoctorView::from).toList();
        }
        return doctorRepository.findByActiveTrue().stream().map(DoctorView::from).toList();
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
        return DoctorView.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeSlotView> listSlots(Long doctorId, LocalDate date) {
        if (!doctorRepository.findById(doctorId).isPresent()) {
            throw new DoctorNotFoundException("Không tìm thấy bác sĩ với ID: " + doctorId);
        }
        if (date != null) {
            return timeSlotRepository.findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctorId, date)
                    .stream().map(TimeSlotView::from).toList();
        }
        return timeSlotRepository.findByDoctorIdAndSlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(doctorId, LocalDate.now())
                .stream().map(TimeSlotView::from).toList();
    }

    @Override
    @Transactional
    public TimeSlotView createTimeSlot(CreateTimeSlotCommand command) {
        if (!doctorRepository.findById(command.doctorId()).isPresent()) {
            throw new DoctorNotFoundException("Không tìm thấy bác sĩ với ID: " + command.doctorId());
        }
        TimeSlot slot = TimeSlot.createNew(
                command.doctorId(),
                command.slotDate(),
                command.startTime(),
                command.endTime()
        );
        TimeSlot saved = timeSlotRepository.save(slot);
        log.info("[DoctorApplicationService] Created slot id={} for doctor={}", saved.getId(), command.doctorId());
        return TimeSlotView.from(saved);
    }

    @PostConstruct
    public void initSampleData() {
        if (doctorRepository.count() == 0) {
            Doctor doc1 = doctorRepository.save(Doctor.createNew(
                    "BS. CKII Nguyễn Văn An",
                    "Tim Mạch",
                    "Khoa Nội Tim Mạch",
                    "Tiến sĩ Y khoa - ĐH Y Dược",
                    18,
                    350000.0,
                    "P.204 - Tầng 2",
                    "Chuyên gia chẩn đoán và điều trị bệnh lý tăng huyết áp, suy tim và can thiệp mạch vành.",
                    "https://images.unsplash.com/photo-1622253692010-333f2da6031d?w=400"
            ));

            Doctor doc2 = doctorRepository.save(Doctor.createNew(
                    "ThS. BS Trần Thị Mai",
                    "Nhi Khoa",
                    "Khoa Nhi",
                    "Thạc sĩ Nhi khoa - ĐH Y Hà Nội",
                    12,
                    300000.0,
                    "P.108 - Tầng 1",
                    "Nhiều năm kinh nghiệm thăm khám, điều trị bệnh hô hấp, tiêu hóa và tư vấn dinh dưỡng cho trẻ nhỏ.",
                    "https://images.unsplash.com/photo-1594824813586-7a71f00845a7?w=400"
            ));

            Doctor doc3 = doctorRepository.save(Doctor.createNew(
                    "BS. CKI Lê Hoàng Quân",
                    "Răng Hàm Mặt",
                    "Khoa Răng Hàm Mặt",
                    "Bác sĩ CKI Nha khoa tổng quát",
                    9,
                    250000.0,
                    "P.302 - Tầng 3",
                    "Khám và điều trị các bệnh lý nha chu, chỉnh nha thẩm mỹ, phục hình răng sứ không xâm lấn.",
                    "https://images.unsplash.com/photo-1537368910025-700350fe46c7?w=400"
            ));

            Doctor doc4 = doctorRepository.save(Doctor.createNew(
                    "PGS. TS Phạm Minh Đức",
                    "Cơ Xương Khớp",
                    "Khoa Cơ Xương Khớp",
                    "Phó Giáo sư, Tiến sĩ Y khoa",
                    22,
                    450000.0,
                    "P.405 - Tầng 4",
                    "Chuyên gia đầu ngành về điều trị thoái hóa khớp, thoát vị đĩa đệm và bệnh gút mạn tính.",
                    "https://images.unsplash.com/photo-1612349317150-e413f6a5b16d?w=400"
            ));

            // Populate time slots for doc1, doc2, doc3, doc4 for today and tomorrow
            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);

            createSlotsForDoctor(doc1.getId(), today);
            createSlotsForDoctor(doc1.getId(), tomorrow);
            createSlotsForDoctor(doc2.getId(), today);
            createSlotsForDoctor(doc2.getId(), tomorrow);
            createSlotsForDoctor(doc3.getId(), today);
            createSlotsForDoctor(doc4.getId(), today);

            log.info("[DoctorApplicationService] Initialized sample doctor & time slot data successfully.");
        }
    }

    private void createSlotsForDoctor(Long doctorId, LocalDate date) {
        timeSlotRepository.save(TimeSlot.createNew(doctorId, date, LocalTime.of(8, 0), LocalTime.of(8, 30)));
        timeSlotRepository.save(TimeSlot.createNew(doctorId, date, LocalTime.of(8, 30), LocalTime.of(9, 0)));
        timeSlotRepository.save(TimeSlot.createNew(doctorId, date, LocalTime.of(9, 0), LocalTime.of(9, 30)));
        timeSlotRepository.save(TimeSlot.createNew(doctorId, date, LocalTime.of(10, 0), LocalTime.of(10, 30)));
        timeSlotRepository.save(TimeSlot.createNew(doctorId, date, LocalTime.of(14, 0), LocalTime.of(14, 30)));
        timeSlotRepository.save(TimeSlot.createNew(doctorId, date, LocalTime.of(14, 30), LocalTime.of(15, 0)));
        timeSlotRepository.save(TimeSlot.createNew(doctorId, date, LocalTime.of(15, 30), LocalTime.of(16, 0)));
    }
}
