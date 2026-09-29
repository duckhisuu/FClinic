package com.fclinic.patientservice.application.result;

import com.fclinic.patientservice.domain.aggregate.Patient;

import java.time.Instant;
import java.time.LocalDate;

public record PatientView(
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
    public static PatientView from(Patient domain) {
        return new PatientView(
                domain.getId(),
                domain.getFullName(),
                domain.getPhoneNumber(),
                domain.getEmail(),
                domain.getDateOfBirth(),
                domain.getGender(),
                domain.getAddress(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}
