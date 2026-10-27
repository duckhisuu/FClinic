package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fclinic.appointmentservice.application.command.AppointmentPageQuery;
import com.fclinic.appointmentservice.application.port.out.AppointmentRepositoryPort;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import com.fclinic.appointmentservice.domain.model.AppointmentStatus;
import com.fclinic.appointmentservice.domain.vo.BookingCode;
import com.fclinic.appointmentservice.infrastructure.entity.JpaAppointmentEntity;
import com.fclinic.appointmentservice.infrastructure.persistence.SpringDataJpaAppointmentRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;

@Component
public class JpaAppointmentRepositoryAdapter implements AppointmentRepositoryPort {
  private final SpringDataJpaAppointmentRepository repository;

  public JpaAppointmentRepositoryAdapter(SpringDataJpaAppointmentRepository repository) {
    this.repository = repository;
  }

  @Override
  public Appointment save(Appointment domain) {
    JpaAppointmentEntity entity;
    if (domain.getId() == null) {
      entity = new JpaAppointmentEntity();
      entity.setVersion(0);
      entity.setCreatedAt(domain.getCreatedAt());
    } else {
      entity = repository.findById(domain.getId()).orElseThrow(AppointmentError::missing);
      if (entity.getVersion() + 1 != domain.getVersion())
        throw AppointmentError.conflict(
            "VERSION_CONFLICT", "Lịch hẹn đã thay đổi. Vui lòng tải lại.");
    }
    entity.setBookingCode(domain.getBookingCode().value());
    entity.setPatientId(domain.getPatientId());
    entity.setPatientName(domain.getPatientName());
    entity.setPatientPhone(domain.getPatientPhone());
    entity.setDoctorId(domain.getDoctorId());
    entity.setDoctorName(domain.getDoctorName());
    entity.setDoctorSpecialty(domain.getDoctorSpecialty());
    entity.setSlotId(domain.getSlotId());
    entity.setAppointmentDate(domain.getAppointmentDate());
    entity.setStartTime(domain.getStartTime());
    entity.setEndTime(domain.getEndTime());
    entity.setReasonForVisit(domain.getReasonForVisit());
    entity.setConsultationFee(domain.getConsultationFee());
    entity.setStatus(domain.getStatus());
    entity.setUpdatedAt(domain.getUpdatedAt());
    return toDomain(repository.saveAndFlush(entity));
  }

  @Override
  public Optional<Appointment> findById(Long id) {
    return repository.findById(id).map(this::toDomain);
  }

  @Override
  public Optional<Appointment> findByIdForUpdate(Long id) {
    return repository.findLockedById(id).map(this::toDomain);
  }

  @Override
  public Set<LocalTime> occupied(long doctor, LocalDate date) {
    return new HashSet<>(repository.occupied(doctor, date));
  }

  @Override
  public List<Long> reminderCandidates(Instant now, Duration lead) {
    return repository.reminderCandidates(now, now.plus(lead), lead.toString());
  }

  @Override
  public AppointmentPageView page(AppointmentPageQuery q) {
    var page =
        repository.findAll(
            (r, query, cb) -> {
              List<Predicate> filters = new ArrayList<>();
              if (q.patientId() != null) filters.add(cb.equal(r.get("patientId"), q.patientId()));
              if (q.doctorId() != null) filters.add(cb.equal(r.get("doctorId"), q.doctorId()));
              if (q.date() != null) filters.add(cb.equal(r.get("appointmentDate"), q.date()));
              if (q.status() != null)
                filters.add(cb.equal(r.get("status"), AppointmentStatus.valueOf(q.status())));
              return cb.and(filters.toArray(Predicate[]::new));
            },
            PageRequest.of(
                q.page(),
                q.size(),
                Sort.by(Sort.Direction.DESC, "appointmentDate", "startTime", "id")));
    return new AppointmentPageView(
        page.getContent().stream().map(this::toDomain).map(AppointmentView::from).toList(),
        q.page(),
        q.size(),
        page.getTotalElements(),
        page.getTotalPages());
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
        e.getVersion(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }
}
