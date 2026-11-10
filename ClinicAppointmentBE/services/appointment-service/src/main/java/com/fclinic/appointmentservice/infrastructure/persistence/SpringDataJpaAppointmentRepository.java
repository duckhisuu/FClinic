package com.fclinic.appointmentservice.infrastructure.persistence;

import com.fclinic.appointmentservice.infrastructure.entity.JpaAppointmentEntity;
import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataJpaAppointmentRepository
    extends JpaRepository<JpaAppointmentEntity, UUID>,
        JpaSpecificationExecutor<JpaAppointmentEntity> {
  @Query("select distinct a.patientId from JpaAppointmentEntity a where a.doctorId=:doctor and "
      + "a.status in (com.fclinic.appointmentservice.domain.model.AppointmentStatus.CONFIRMED, "
      + "com.fclinic.appointmentservice.domain.model.AppointmentStatus.COMPLETED) order by a.patientId")
  List<UUID> assignedPatients(@Param("doctor") UUID doctor, org.springframework.data.domain.Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from JpaAppointmentEntity a where a.id=:id")
  Optional<JpaAppointmentEntity> findLockedById(@Param("id") UUID id);

  @Query(
      "select a.startTime from JpaAppointmentEntity a where a.doctorId=:doctor and"
          + " a.appointmentDate=:date and"
          + " a.status=com.fclinic.appointmentservice.domain.model.AppointmentStatus.CONFIRMED")
  List<LocalTime> occupied(@Param("doctor") UUID doctor, @Param("date") LocalDate date);

  @Query(
      value =
          "SELECT a.id FROM appointments a WHERE a.status='CONFIRMED' AND"
              + " ((a.appointment_date+a.start_time) AT TIME ZONE 'Asia/Saigon')>:now AND"
              + " ((a.appointment_date+a.start_time) AT TIME ZONE 'Asia/Saigon')<=:through AND NOT"
              + " EXISTS (SELECT 1 FROM reminder_publications r WHERE r.appointment_id=a.id AND"
              + " r.aggregate_version=a.version AND"
              + " r.reminder_due_at=(((a.appointment_date+a.start_time) AT TIME ZONE"
              + " 'Asia/Saigon')-CAST(:lead AS interval))) ORDER BY"
              + " a.appointment_date,a.start_time,a.id LIMIT 1000",
      nativeQuery = true)
  List<UUID> reminderCandidates(
      @Param("now") Instant now, @Param("through") Instant through, @Param("lead") String lead);
}
