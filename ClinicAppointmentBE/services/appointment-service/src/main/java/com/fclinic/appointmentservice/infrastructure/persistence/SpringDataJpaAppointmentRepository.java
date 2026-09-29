package com.fclinic.appointmentservice.infrastructure.persistence;

import com.fclinic.appointmentservice.domain.model.AppointmentStatus;
import com.fclinic.appointmentservice.infrastructure.entity.JpaAppointmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataJpaAppointmentRepository extends JpaRepository<JpaAppointmentEntity, Long> {

    Optional<JpaAppointmentEntity> findByBookingCode(String bookingCode);

    List<JpaAppointmentEntity> findByPatientIdOrderByAppointmentDateDesc(Long patientId);

    List<JpaAppointmentEntity> findByPatientPhoneOrderByAppointmentDateDesc(String patientPhone);

    boolean existsByDoctorIdAndSlotIdAndAppointmentDateAndStatusNot(
            Long doctorId, Long slotId, LocalDate appointmentDate, AppointmentStatus status);
}
