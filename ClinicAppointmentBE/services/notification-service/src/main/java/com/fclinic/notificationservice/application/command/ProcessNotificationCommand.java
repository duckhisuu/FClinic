package com.fclinic.notificationservice.application.command;

public record ProcessNotificationCommand(
        String bookingCode,
        String recipientName,
        String recipientPhone,
        String doctorName,
        String appointmentTime,
        String channel,
        String message
) {}
