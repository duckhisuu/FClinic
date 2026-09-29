package com.fclinic.notificationservice.application.result;

import com.fclinic.notificationservice.domain.aggregate.NotificationLog;

import java.time.Instant;

public record NotificationView(
        String id,
        String bookingCode,
        String recipientName,
        String recipientPhone,
        String doctorName,
        String appointmentTime,
        String channel,
        String message,
        String status,
        Instant createdAt
) {
    public static NotificationView from(NotificationLog domain) {
        return new NotificationView(
                domain.getId(),
                domain.getBookingCode(),
                domain.getRecipientName(),
                domain.getRecipientPhone(),
                domain.getDoctorName(),
                domain.getAppointmentTime(),
                domain.getChannel(),
                domain.getMessage(),
                domain.getStatus(),
                domain.getCreatedAt()
        );
    }
}
