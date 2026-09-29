package com.fclinic.appointmentservice.domain.repository;

import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import com.fclinic.appointmentservice.domain.model.AppointmentStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository {

    Appointment save(Appointment appointment);

    Optional<Appointment> findById(Long id);

    Optional<Appointment> findByBookingCode(String bookingCode);

    List<Appointment> findByPatientIdOrderByAppointmentDateDesc(Long patientId);

    List<Appointment> findByPatientPhoneOrderByAppointmentDateDesc(String phone);

    List<Appointment> findAll();

    boolean existsByDoctorIdAndSlotIdAndAppointmentDateAndStatusNot(
            Long doctorId, Long slotId, LocalDate appointmentDate, AppointmentStatus status);

    long count();
}
