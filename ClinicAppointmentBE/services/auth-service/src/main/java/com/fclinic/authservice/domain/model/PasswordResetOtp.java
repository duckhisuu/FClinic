package com.fclinic.authservice.domain.model;

import com.fclinic.authservice.domain.exception.AuthValidationException;
import com.fclinic.authservice.domain.util.UuidV4;

import java.time.Instant;
import java.util.UUID;

public class PasswordResetOtp {

    private final UUID id;
    private final UUID userId;
    private final String email;
    private final String otpCode;
    private final Instant expiresAt;
    private boolean used;
    private final Instant createdAt;
    private Instant updatedAt;

    public PasswordResetOtp(
            UUID id,
            UUID userId,
            String email,
            String otpCode,
            Instant expiresAt,
            boolean used,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = UuidV4.requireValid(id, "otpId");
        this.userId = UuidV4.requireValid(userId, "userId");
        if (email == null || email.isBlank()) {
            throw new AuthValidationException("email không được để trống");
        }
        if (otpCode == null || otpCode.isBlank()) {
            throw new AuthValidationException("otpCode không được để trống");
        }
        this.email = email.trim().toLowerCase();
        this.otpCode = otpCode.trim();
        this.expiresAt = expiresAt;
        this.used = used;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public static PasswordResetOtp issue(UUID userId, String email, String otpCode, Instant now, long ttlSeconds) {
        return new PasswordResetOtp(
                UuidV4.generate(),
                userId,
                email,
                otpCode,
                now.plusSeconds(ttlSeconds),
                false,
                now,
                now
        );
    }

    public boolean isValidFor(String candidateOtp, Instant now) {
        return !used && expiresAt != null && now.isBefore(expiresAt) && otpCode.equals(candidateOtp);
    }

    public void markUsed(Instant now) {
        this.used = true;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

