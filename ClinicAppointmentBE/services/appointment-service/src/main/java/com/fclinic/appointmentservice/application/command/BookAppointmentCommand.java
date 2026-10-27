package com.fclinic.appointmentservice.application.command;

import java.time.LocalDate;
import java.time.LocalTime;

public record BookAppointmentCommand(
        Long patientId,
        String patientName,
        String patientPhone,
        String patientEmail,
        Long doctorId,
        String doctorName,
        String doctorSpecialty,
        String doctorEmail,
        Long slotId,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        String reasonForVisit,
        Double consultationFee
) {
    public BookAppointmentCommand(
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
            Double consultationFee
    ) {
        this(patientId, patientName, patientPhone, null, doctorId, doctorName, doctorSpecialty, null,
                slotId, appointmentDate, startTime, endTime, reasonForVisit, consultationFee);
    }
}
