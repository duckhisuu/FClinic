package com.fclinic.notificationservice.domain.aggregate;

import java.time.Instant;
import java.util.UUID;

public class NotificationLog {

    private String id;
    private String bookingCode;
    private String recipientName;
    private String recipientPhone;
    private String doctorName;
    private String appointmentTime;
    private String channel;
    private String message;
    private String status;
    private Instant createdAt;

    public NotificationLog(
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
        this.id = id;
        this.bookingCode = bookingCode;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.doctorName = doctorName;
        this.appointmentTime = appointmentTime;
        this.channel = channel;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static NotificationLog create(
            String bookingCode,
            String recipientName,
            String recipientPhone,
            String doctorName,
            String appointmentTime,
            String channel,
            String message
    ) {
        return new NotificationLog(
                UUID.randomUUID().toString(),
                bookingCode,
                recipientName,
                recipientPhone,
                doctorName,
                appointmentTime,
                channel != null ? channel : "SMS",
                message,
                "SENT",
                Instant.now()
        );
    }

    public String getId() { return id; }
    public String getBookingCode() { return bookingCode; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getDoctorName() { return doctorName; }
    public String getAppointmentTime() { return appointmentTime; }
    public String getChannel() { return channel; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
