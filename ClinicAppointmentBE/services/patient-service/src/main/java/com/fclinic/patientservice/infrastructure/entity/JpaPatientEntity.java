package com.fclinic.patientservice.infrastructure.entity;
import com.fclinic.patientservice.domain.model.ProfileData;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name="patients")
@Getter @Setter
public class JpaPatientEntity {
    @Id
    private UUID id;
    @Column(name="legacy_patient_id")
    private Long legacyPatientId;
    @Column(name="patient_code", nullable=false, unique=true, length=40)
    private String patientCode;
    @Column(name="full_name", nullable=false, length=255)
    private String fullName;
    @Column(name="phone_number", nullable=false, length=16)
    private String phoneNumber;
    @Column(length=255)
    private String email;
    @Column(name="date_of_birth")
    private LocalDate dateOfBirth;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=10)
    private ProfileData.Gender gender;
    @Column(length=2000)
    private String address;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=8)
    private ProfileData.Status status;
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
