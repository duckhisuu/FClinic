package com.fclinic.appointmentservice.infrastructure.entity;

import com.fclinic.appointmentservice.domain.model.AppointmentStatus;
import com.fclinic.common.web.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JpaAppointmentEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_code", nullable = false, unique = true, length = 32)
    private String bookingCode;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "patient_name", nullable = false)
    private String patientName;

    @Column(name = "patient_phone", nullable = false, length = 20)
    private String patientPhone;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "doctor_name", nullable = false)
    private String doctorName;

    @Column(name = "doctor_specialty", nullable = false)
    private String doctorSpecialty;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "reason_for_visit", columnDefinition = "TEXT")
    private String reasonForVisit;

    @Column(name = "consultation_fee")
    private Double consultationFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;

    public JpaAppointmentEntity(
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
            AppointmentStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        super(createdAt, updatedAt);
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
    }
}
