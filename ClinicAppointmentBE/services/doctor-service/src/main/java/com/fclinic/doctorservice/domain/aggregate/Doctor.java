package com.fclinic.doctorservice.domain.aggregate;

import java.time.Instant;
import java.util.UUID;

public class Doctor {

    private UUID id;
    private String name;
    private String specialty;
    private String department;
    private String qualification;
    private Integer experienceYears;
    private Double consultationFee;
    private String roomNumber;
    private String bio;
    private String avatarUrl;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public Doctor(
            UUID id,
            String name,
            String specialty,
            String department,
            String qualification,
            Integer experienceYears,
            Double consultationFee,
            String roomNumber,
            String bio,
            String avatarUrl,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.name = name;
        this.specialty = specialty;
        this.department = department;
        this.qualification = qualification;
        this.experienceYears = experienceYears;
        this.consultationFee = consultationFee;
        this.roomNumber = roomNumber;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.active = active;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static Doctor createNew(
            String name,
            String specialty,
            String department,
            String qualification,
            Integer experienceYears,
            Double consultationFee,
            String roomNumber,
            String bio,
            String avatarUrl
    ) {
        if (consultationFee == null || !Double.isFinite(consultationFee) || consultationFee < 0)
            throw new IllegalArgumentException("Phí khám phải là số hữu hạn không âm");
        if (experienceYears != null && experienceYears < 0)
            throw new IllegalArgumentException("Số năm kinh nghiệm phải không âm");
        Instant now = Instant.now();
        return new Doctor(
                UUID.randomUUID(),
                name,
                specialty,
                department,
                qualification,
                experienceYears,
                consultationFee,
                roomNumber,
                bio,
                avatarUrl,
                true,
                now,
                now
        );
    }

    public void updateStatus(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public String getSpecialty() { return specialty; }
    public String getDepartment() { return department; }
    public String getQualification() { return qualification; }
    public Integer getExperienceYears() { return experienceYears; }
    public Double getConsultationFee() { return consultationFee; }
    public String getRoomNumber() { return roomNumber; }
    public String getBio() { return bio; }
    public String getAvatarUrl() { return avatarUrl; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
