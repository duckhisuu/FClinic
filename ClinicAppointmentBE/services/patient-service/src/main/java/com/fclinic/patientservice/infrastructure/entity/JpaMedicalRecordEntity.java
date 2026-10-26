package com.fclinic.patientservice.infrastructure.entity;
import com.fclinic.patientservice.domain.model.ProfileData;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name="medical_records")
@Getter @Setter
public class JpaMedicalRecordEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="patient_id", nullable=false)
    private UUID patientId;
    @Column(name="doctor_id", nullable=false)
    private Long doctorId;
    @Column(name="appointment_id", unique=true)
    private Long appointmentId;
    @Column(name="visit_date", nullable=false)
    private Instant visitDate;
    @Column(nullable=false, length=2000)
    private String symptoms;
    @Column(nullable=false, length=2000)
    private String diagnosis;
    @Column(length=4000)
    private String treatment;
    @Column(length=4000)
    private String notes;
    @Version @Column(nullable=false)
    private Long version;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
    @Column(name="updated_at", nullable=false)
    private Instant updatedAt;
    @Column(name="created_by", nullable=false, length=255)
    private String createdBy;
    @Column(name="updated_by", nullable=false, length=255)
    private String updatedBy;
}
