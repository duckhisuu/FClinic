package com.fclinic.patientservice.domain.model;
import java.time.LocalDate;

public record ProfileData(String fullName, String phoneNumber, String email, LocalDate dateOfBirth,
                          Gender gender, String address, Status status) {
    public enum Gender { MALE, FEMALE, OTHER, UNKNOWN }
    public enum Status { ACTIVE, INACTIVE }
}
