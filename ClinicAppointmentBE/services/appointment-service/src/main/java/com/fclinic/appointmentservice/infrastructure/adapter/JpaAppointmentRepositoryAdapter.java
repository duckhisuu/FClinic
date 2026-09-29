package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fclinic.appointmentservice.application.port.out.AppointmentRepositoryPort;
import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import com.fclinic.appointmentservice.domain.model.AppointmentStatus;
import com.fclinic.appointmentservice.domain.repository.AppointmentRepository;
import com.fclinic.appointmentservice.domain.vo.BookingCode;
import com.fclinic.appointmentservice.infrastructure.entity.JpaAppointmentEntity;
import com.fclinic.appointmentservice.infrastructure.persistence.SpringDataJpaAppointmentRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class JpaAppointmentRepositoryAdapter implements AppointmentRepositoryPort, AppointmentRepository {

    private final SpringDataJpaAppointmentRepository repository;

    public JpaAppointmentRepositoryAdapter(SpringDataJpaAppointmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public Appointment save(Appointment domain) {
        JpaAppointmentEntity entity = toEntity(domain);
        JpaAppointmentEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Appointment> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Appointment> findByBookingCode(String bookingCode) {
        return repository.findByBookingCode(bookingCode).map(this::toDomain);
    }

    @Override
    public List<Appointment> findByPatientIdOrderByAppointmentDateDesc(Long patientId) {
        return repository.findByPatientIdOrderByAppointmentDateDesc(patientId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Appointment> findByPatientPhoneOrderByAppointmentDateDesc(String phone) {
        return repository.findByPatientPhoneOrderByAppointmentDateDesc(phone)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Appointment> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByDoctorIdAndSlotIdAndAppointmentDateAndStatusNot(
            Long doctorId, Long slotId, LocalDate appointmentDate, AppointmentStatus status) {
        return repository.existsByDoctorIdAndSlotIdAndAppointmentDateAndStatusNot(
                doctorId, slotId, appointmentDate, status);
    }

    @Override
    public long count() {
        return repository.count();
    }

    private JpaAppointmentEntity toEntity(Appointment d) {
        return new JpaAppointmentEntity(
                d.getId(),
                d.getBookingCode().value(),
                d.getPatientId(),
                d.getPatientName(),
                d.getPatientPhone(),
                d.getDoctorId(),
                d.getDoctorName(),
                d.getDoctorSpecialty(),
                d.getSlotId(),
                d.getAppointmentDate(),
                d.getStartTime(),
                d.getEndTime(),
                d.getReasonForVisit(),
                d.getConsultationFee(),
                d.getStatus(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }

    private Appointment toDomain(JpaAppointmentEntity e) {
        return new Appointment(
                e.getId(),
                BookingCode.of(e.getBookingCode()),
                e.getPatientId(),
                e.getPatientName(),
                e.getPatientPhone(),
                e.getDoctorId(),
                e.getDoctorName(),
                e.getDoctorSpecialty(),
                e.getSlotId(),
                e.getAppointmentDate(),
                e.getStartTime(),
                e.getEndTime(),
                e.getReasonForVisit(),
                e.getConsultationFee(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
