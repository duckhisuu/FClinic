package com.fclinic.notificationservice.api.dto;

import com.fclinic.notificationservice.application.result.NotificationView;

import java.time.Instant;

public record NotificationResponse(
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
    public static NotificationResponse from(NotificationView view) {
        return new NotificationResponse(
                view.id(),
                view.bookingCode(),
                view.recipientName(),
                view.recipientPhone(),
                view.doctorName(),
                view.appointmentTime(),
                view.channel(),
                view.message(),
                view.status(),
                view.createdAt()
        );
    }
}
