package com.fclinic.appointmentservice.api.dto;

import com.fclinic.appointmentservice.application.result.AppointmentView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentResponse(
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
    public static AppointmentResponse from(AppointmentView view) {
        return new AppointmentResponse(
                view.id(),
                view.bookingCode(),
                view.patientId(),
                view.patientName(),
                view.patientPhone(),
                view.doctorId(),
                view.doctorName(),
                view.doctorSpecialty(),
                view.slotId(),
                view.appointmentDate(),
                view.startTime(),
                view.endTime(),
                view.reasonForVisit(),
                view.consultationFee(),
                view.status(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}
