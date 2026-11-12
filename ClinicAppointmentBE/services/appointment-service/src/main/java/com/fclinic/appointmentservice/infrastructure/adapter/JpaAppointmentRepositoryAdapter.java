package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fclinic.appointmentservice.application.command.AppointmentPageQuery;
import com.fclinic.appointmentservice.application.port.out.AppointmentRepositoryPort;
import com.fclinic.appointmentservice.application.result.*;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import com.fclinic.appointmentservice.domain.model.AppointmentStatus;
import com.fclinic.appointmentservice.infrastructure.persistence.mapper.AppointmentPersistenceMapper;
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

  private final AppointmentPersistenceMapper mapper;

  public JpaAppointmentRepositoryAdapter(SpringDataJpaAppointmentRepository repository, AppointmentPersistenceMapper mapper) {
    this.mapper = mapper;
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
    mapper.updateEntity(domain, entity);
    return mapper.toDomain(repository.saveAndFlush(entity));
  }

  @Override
  public Optional<Appointment> findById(UUID id) {
    return repository.findById(id).map(mapper::toDomain);
  }

  @Override
  public Optional<Appointment> findByIdForUpdate(UUID id) {
    return repository.findLockedById(id).map(mapper::toDomain);
  }

  @Override
  public Set<LocalTime> occupied(UUID doctor, LocalDate date) {
    return new HashSet<>(repository.occupied(doctor, date));
  }

  @Override
  public List<UUID> reminderCandidates(Instant now, Duration lead) {
    return repository.reminderCandidates(now, now.plus(lead), lead.toString());
  }

  @Override
  public AppointmentPageView page(AppointmentPageQuery q) {
    var page =
        repository.findAll(
            (r, query, cb) -> {
              List<Predicate> filters = new ArrayList<>();
              if (q.allowedPatientIds() != null)
                filters.add(q.allowedPatientIds().isEmpty() ? cb.disjunction() : r.get("patientId").in(q.allowedPatientIds()));
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
        page.getContent().stream().map(mapper::toDomain).map(AppointmentView::from).toList(),
        q.page(),
        q.size(),
        page.getTotalElements(),
        page.getTotalPages());
  }

}
