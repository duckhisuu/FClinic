package com.fclinic.doctorservice.domain.aggregate;

import java.time.Instant;

public class Doctor {

    private Long id;
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
            Long id,
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
        this.id = id;
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
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
        Instant now = Instant.now();
        return new Doctor(
                null,
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
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
