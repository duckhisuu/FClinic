package com.fclinic.patientservice.api.dto;

import com.fclinic.patientservice.application.result.PatientView;

import java.time.Instant;
import java.time.LocalDate;

public record PatientResponse(
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
    public static PatientResponse from(PatientView view) {
        return new PatientResponse(
                view.id(),
                view.fullName(),
                view.phoneNumber(),
                view.email(),
                view.dateOfBirth(),
                view.gender(),
                view.address(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}
