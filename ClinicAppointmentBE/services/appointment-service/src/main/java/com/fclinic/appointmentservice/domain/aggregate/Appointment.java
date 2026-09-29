package com.fclinic.appointmentservice.domain.aggregate;

import com.fclinic.appointmentservice.domain.model.AppointmentStatus;
import com.fclinic.appointmentservice.domain.vo.BookingCode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public class Appointment {

    private Long id;
    private BookingCode bookingCode;
    private Long patientId;
    private String patientName;
    private String patientPhone;
    private Long doctorId;
    private String doctorName;
    private String doctorSpecialty;
    private Long slotId;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String reasonForVisit;
    private Double consultationFee;
    private AppointmentStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public Appointment(
            Long id,
            BookingCode bookingCode,
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
            AppointmentStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.bookingCode = bookingCode;
        this.patientId = patientId;
        this.patientName = patientName;
        this.patientPhone = patientPhone;
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.doctorSpecialty = doctorSpecialty;
        this.slotId = slotId;
        this.appointmentDate = appointmentDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reasonForVisit = reasonForVisit;
        this.consultationFee = consultationFee;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Appointment createNew(
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
        Instant now = Instant.now();
        return new Appointment(
                null,
                BookingCode.generate(),
                patientId,
                patientName,
                patientPhone,
                doctorId,
                doctorName,
                doctorSpecialty,
                slotId,
                appointmentDate,
                startTime,
                endTime,
                reasonForVisit,
                consultationFee,
                AppointmentStatus.CONFIRMED,
                now,
                now
        );
    }

    public void cancel() {
        if (this.status == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("Cuộc hẹn đã ở trạng thái đã hủy.");
        }
        this.status = AppointmentStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        this.status = AppointmentStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BookingCode getBookingCode() {
        return bookingCode;
    }

    public Long getPatientId() {
        return patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getPatientPhone() {
        return patientPhone;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public String getDoctorSpecialty() {
        return doctorSpecialty;
    }

    public Long getSlotId() {
        return slotId;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getReasonForVisit() {
        return reasonForVisit;
    }

    public Double getConsultationFee() {
        return consultationFee;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
