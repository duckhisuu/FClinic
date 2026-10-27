package com.fclinic.appointmentservice.application.usecase;

import com.fclinic.appointmentservice.application.command.BookAppointmentCommand;
import com.fclinic.appointmentservice.application.command.CancelAppointmentCommand;
import com.fclinic.appointmentservice.application.exception.AppointmentNotFoundException;
import com.fclinic.appointmentservice.application.exception.SlotAlreadyBookedException;
import com.fclinic.appointmentservice.application.port.in.BookAppointmentUseCase;
import com.fclinic.appointmentservice.application.port.in.CancelAppointmentUseCase;
import com.fclinic.appointmentservice.application.port.in.GetAppointmentUseCase;
import com.fclinic.appointmentservice.application.port.in.ListAppointmentsUseCase;
import com.fclinic.appointmentservice.application.port.out.AppointmentEventPublisherPort;
import com.fclinic.appointmentservice.application.port.out.AppointmentRepositoryPort;
import com.fclinic.appointmentservice.application.port.out.DistributedLockPort;
import com.fclinic.appointmentservice.application.result.AppointmentView;
import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import com.fclinic.appointmentservice.domain.event.AppointmentBookedDomainEvent;
import com.fclinic.appointmentservice.domain.event.AppointmentCancelledDomainEvent;
import com.fclinic.appointmentservice.domain.model.AppointmentStatus;
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
public class AppointmentApplicationService implements
        BookAppointmentUseCase,
        CancelAppointmentUseCase,
        GetAppointmentUseCase,
        ListAppointmentsUseCase {

    private static final Logger log = LoggerFactory.getLogger(AppointmentApplicationService.class);

    private final AppointmentRepositoryPort appointmentRepository;
    private final AppointmentEventPublisherPort eventPublisher;
    private final DistributedLockPort lockPort;

    public AppointmentApplicationService(
            AppointmentRepositoryPort appointmentRepository,
            AppointmentEventPublisherPort eventPublisher,
            DistributedLockPort lockPort
    ) {
        this.appointmentRepository = appointmentRepository;
        this.eventPublisher = eventPublisher;
        this.lockPort = lockPort;
    }

    @Override
    public AppointmentView bookAppointment(BookAppointmentCommand command) {
        String lockKey = String.format("lock:slot:%d:%d:%s",
                command.doctorId(), command.slotId(), command.appointmentDate());

        return lockPort.executeWithLock(lockKey, 5, 10, () -> executeBookingInTransaction(command));
    }

    @Transactional
    public AppointmentView executeBookingInTransaction(BookAppointmentCommand command) {
        boolean alreadyBooked = appointmentRepository.existsByDoctorIdAndSlotIdAndAppointmentDateAndStatusNot(
                command.doctorId(),
                command.slotId(),
                command.appointmentDate(),
                AppointmentStatus.CANCELLED
        );

        if (alreadyBooked) {
            throw new SlotAlreadyBookedException("Khung giờ này đã có bệnh nhân đặt. Vui lòng chọn khung giờ khác.");
        }

        Appointment appointment = Appointment.createNew(
                command.patientId(),
                command.patientName(),
                command.patientPhone(),
                command.doctorId(),
                command.doctorName(),
                command.doctorSpecialty(),
                command.slotId(),
                command.appointmentDate(),
                command.startTime(),
                command.endTime(),
                command.reasonForVisit(),
                command.consultationFee()
        );

        Appointment saved = appointmentRepository.save(appointment);

        // Transactional Outbox: Event is stored in outbox_events table in the SAME transaction!
        AppointmentBookedDomainEvent domainEvent = AppointmentBookedDomainEvent.from(
                saved.getId(),
                saved.getBookingCode().value(),
                saved.getPatientId(),
                saved.getPatientName(),
                saved.getPatientPhone(),
                saved.getDoctorId(),
                saved.getDoctorName(),
                saved.getDoctorSpecialty(),
                saved.getSlotId(),
                saved.getAppointmentDate(),
                saved.getStartTime(),
                saved.getEndTime(),
                saved.getReasonForVisit(),
                saved.getConsultationFee()
        );
        eventPublisher.publish(domainEvent);

        log.info("[AppointmentApplicationService] Booked appointment code={} id={}",
                saved.getBookingCode().value(), saved.getId());

        return AppointmentView.from(saved);
    }

    @Override
    @Transactional
    public AppointmentView cancelAppointment(CancelAppointmentCommand command) {
        Appointment appointment = appointmentRepository.findById(command.appointmentId())
                .orElseThrow(() -> new AppointmentNotFoundException("Không tìm thấy cuộc hẹn ID: " + command.appointmentId()));

        appointment.cancel();
        Appointment saved = appointmentRepository.save(appointment);

        AppointmentCancelledDomainEvent event = AppointmentCancelledDomainEvent.from(
                saved.getId(),
                saved.getBookingCode().value(),
                saved.getPatientPhone(),
                command.reason() != null ? command.reason() : "Bệnh nhân yêu cầu hủy"
        );
        eventPublisher.publish(event);

        log.info("[AppointmentApplicationService] Cancelled appointment code={}", saved.getBookingCode().value());
        return AppointmentView.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AppointmentView> getById(Long id) {
        return appointmentRepository.findById(id).map(AppointmentView::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AppointmentView> getByBookingCode(String bookingCode) {
        return appointmentRepository.findByBookingCode(bookingCode).map(AppointmentView::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentView> getAll() {
        return appointmentRepository.findAll().stream().map(AppointmentView::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentView> getByPatientId(Long patientId) {
        return appointmentRepository.findByPatientIdOrderByAppointmentDateDesc(patientId)
                .stream().map(AppointmentView::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentView> getByPatientPhone(String phone) {
        return appointmentRepository.findByPatientPhoneOrderByAppointmentDateDesc(phone)
                .stream().map(AppointmentView::from).toList();
    }

    @PostConstruct
    public void initSampleData() {
        if (appointmentRepository.count() == 0) {
            LocalDate today = LocalDate.now();

            Appointment appt1 = Appointment.createNew(
                    1L,
                    "Nguyễn Văn Hùng",
                    "0912345678",
                    1L,
                    "BS. CKII Nguyễn Văn An",
                    "Tim Mạch",
                    1L,
                    today,
                    LocalTime.of(8, 0),
                    LocalTime.of(8, 30),
                    "Kiểm tra định kỳ huyết áp và đau thắt ngực",
                    350000.0
            );
            appointmentRepository.save(appt1);

            Appointment appt2 = Appointment.createNew(
                    2L,
                    "Lê Thuỳ Linh",
                    "0987654321",
                    2L,
                    "ThS. BS Trần Thị Mai",
                    "Nhi Khoa",
                    2L,
                    today.plusDays(1),
                    LocalTime.of(14, 0),
                    LocalTime.of(14, 30),
                    "Bé ho sốt kéo dài 2 ngày",
                    300000.0
            );
            appointmentRepository.save(appt2);
            log.info("[AppointmentApplicationService] Initialized sample appointment data");
        }
    }
}
