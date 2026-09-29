package com.fclinic.patientservice.domain.aggregate;

import java.time.Instant;
import java.time.LocalDate;

public class Patient {

    private Long id;
    private String fullName;
    private String phoneNumber;
    private String email;
    private LocalDate dateOfBirth;
    private String gender;
    private String address;
    private Instant createdAt;
    private Instant updatedAt;

    public Patient(
            Long id,
            String fullName,
            String phoneNumber,
            String email,
            LocalDate dateOfBirth,
            String gender,
            String address,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.address = address;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Patient createNew(
            String fullName,
            String phoneNumber,
            String email,
            LocalDate dateOfBirth,
            String gender,
            String address
    ) {
        Instant now = Instant.now();
        return new Patient(
                null,
                fullName,
                phoneNumber,
                email,
                dateOfBirth,
                gender,
                address,
                now,
                now
        );
    }

    public void updateInfo(String fullName, String email, LocalDate dateOfBirth, String gender, String address) {
        this.fullName = fullName;
        this.email = email;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.address = address;
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFullName() { return fullName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getEmail() { return email; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getGender() { return gender; }
    public String getAddress() { return address; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
