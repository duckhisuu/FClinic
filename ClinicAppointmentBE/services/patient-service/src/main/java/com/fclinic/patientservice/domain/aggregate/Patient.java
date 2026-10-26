package com.fclinic.patientservice.domain.aggregate;
import com.fclinic.patientservice.domain.model.ProfileData;
import com.fclinic.patientservice.domain.model.ProfileData.Gender;
import com.fclinic.patientservice.domain.model.ProfileData.Status;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Patient(UUID id, String patientCode, String fullName, String phoneNumber, String email,
                      LocalDate dateOfBirth, Gender gender, String address, Status status, long version,
                      Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {
    public static Patient create(ProfileData data, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        return new Patient(id, "PAT-" + id, data.fullName(), data.phoneNumber(), data.email(),
                data.dateOfBirth(), data.gender(), data.address(), Status.ACTIVE, 0, now, now, actor, actor);
    }
    public ProfileData profile() {
        return new ProfileData(fullName, phoneNumber, email, dateOfBirth, gender, address, status);
    }
    public Patient replace(ProfileData data, String actor, Instant now) {
        return new Patient(id, patientCode, data.fullName(), data.phoneNumber(), data.email(),
                data.dateOfBirth(), data.gender(), data.address(), data.status(), version,
                createdAt, now, createdBy, actor);
    }
}
