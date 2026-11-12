package com.fclinic.appointmentservice.infrastructure.persistence.mapper;

import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import com.fclinic.appointmentservice.domain.vo.BookingCode;
import com.fclinic.appointmentservice.infrastructure.entity.JpaAppointmentEntity;
import org.springframework.stereotype.Component;

@Component
public class AppointmentPersistenceMapper {
  public void updateEntity(Appointment domain, JpaAppointmentEntity entity) {
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
  }

  public Appointment toDomain(JpaAppointmentEntity e) {
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
