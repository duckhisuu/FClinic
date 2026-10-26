package com.fclinic.patientservice.api.dto;
import com.fclinic.patientservice.domain.aggregate.Patient;
import com.fclinic.patientservice.domain.model.ProfileData.Gender;
import com.fclinic.patientservice.domain.model.ProfileData.Status;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PatientResponse(UUID id, String patientCode, String fullName, String phoneNumber, String email,
                              LocalDate dateOfBirth, Gender gender, String address, Status status, long version,
                              Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {
    public static PatientResponse from(Patient p) {
        return new PatientResponse(p.id(), p.patientCode(), p.fullName(), p.phoneNumber(), p.email(), p.dateOfBirth(),
                p.gender(), p.address(), p.status(), p.version(), p.createdAt(), p.updatedAt(), p.createdBy(), p.updatedBy());
    }
}
