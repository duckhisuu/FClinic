package com.fclinic.authservice.domain.aggregate;

import com.fclinic.authservice.domain.exception.AuthValidationException;
import com.fclinic.authservice.domain.util.UuidV4;
import com.fclinic.authservice.domain.valueobject.Role;
import com.fclinic.authservice.domain.valueobject.RoleName;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class User {

    private final UUID id;
    private final UUID keycloakId;
    private final String username;
    private String email;
    private String phoneNumber;
    private String fullName;
    private Role role;
    private UUID patientId;
    private UUID doctorId;
    private String googleId;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public User(
            UUID id,
            UUID keycloakId,
            String username,
            String email,
            String phoneNumber,
            String fullName,
            Role role,
            UUID patientId,
            UUID doctorId,
            String googleId,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = UuidV4.requireValid(id, "userId");
        this.keycloakId = UuidV4.requireValid(keycloakId, "keycloakId");
        this.username = requireNonBlank(username, "username").toLowerCase();
        this.email = requireNonBlank(email, "email").toLowerCase();
        this.phoneNumber = normalizeNullable(phoneNumber);
        this.fullName = requireNonBlank(fullName, "fullName");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.patientId = UuidV4.requireValidNullable(patientId, "patientId");
        this.doctorId = UuidV4.requireValidNullable(doctorId, "doctorId");
        this.googleId = normalizeNullable(googleId);
        this.active = active;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public static User create(
            UUID userId,
            UUID keycloakId,
            String username,
            String email,
            String phoneNumber,
            String fullName,
            Role role,
            UUID patientId,
            UUID doctorId,
            String googleId
    ) {
        Instant now = Instant.now();
        UUID id = userId != null ? userId : UuidV4.generate();
        return new User(
                id,
                keycloakId,
                username,
                email,
                phoneNumber,
                fullName,
                role,
                patientId,
                doctorId,
                googleId,
                true,
                now,
                now
        );
    }

    public void updateProfile(String fullName, String email, String phoneNumber) {
        if (fullName != null && !fullName.isBlank()) {
            this.fullName = fullName.trim();
        }
        if (email != null && !email.isBlank()) {
            this.email = email.trim().toLowerCase();
        }
        if (phoneNumber != null) {
            this.phoneNumber = normalizeNullable(phoneNumber);
        }
        this.updatedAt = Instant.now();
    }

    public void changeRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole, "role must not be null");
        this.updatedAt = Instant.now();
    }

    public void setActiveStatus(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void linkPatientId(UUID patientId) {
        this.patientId = UuidV4.requireValidNullable(patientId, "patientId");
        this.updatedAt = Instant.now();
    }

    public void linkDoctorId(UUID doctorId) {
        this.doctorId = UuidV4.requireValidNullable(doctorId, "doctorId");
        this.updatedAt = Instant.now();
    }

    public void linkGoogleId(String googleId) {
        if (googleId != null && !googleId.isBlank()) {
            this.googleId = googleId.trim();
            this.updatedAt = Instant.now();
        }
    }

    public RoleName getRoleName() {
        return role.getName();
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AuthValidationException(fieldName + " không được để trống");
        }
        return value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public UUID getKeycloakId() {
        return keycloakId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public String getGoogleId() {
        return googleId;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

