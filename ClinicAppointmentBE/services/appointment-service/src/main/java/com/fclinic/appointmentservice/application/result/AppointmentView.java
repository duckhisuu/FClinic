package com.fclinic.appointmentservice.application.result;

import com.fclinic.appointmentservice.domain.aggregate.Appointment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentView(
        Long id,
        String bookingCode,
        Long patientId,
        String patientName,
        String patientPhone,
        Long doctorId,
        String doctorName,
        String doctorSpecialty,
        Long slotId,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        String reasonForVisit,
        Double consultationFee,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static AppointmentView from(Appointment domain) {
        return new AppointmentView(
                domain.getId(),
                domain.getBookingCode().value(),
                domain.getPatientId(),
                domain.getPatientName(),
                domain.getPatientPhone(),
                domain.getDoctorId(),
                domain.getDoctorName(),
                domain.getDoctorSpecialty(),
                domain.getSlotId(),
                domain.getAppointmentDate(),
                domain.getStartTime(),
                domain.getEndTime(),
                domain.getReasonForVisit(),
                domain.getConsultationFee(),
                domain.getStatus().name(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}
