package com.fclinic.appointmentservice.application.command;

public record CancelAppointmentCommand(
        Long appointmentId,
        String reason
) {}
