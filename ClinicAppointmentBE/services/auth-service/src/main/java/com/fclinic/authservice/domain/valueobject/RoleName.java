package com.fclinic.authservice.domain.valueobject;

import com.fclinic.authservice.domain.exception.AuthValidationException;

import java.util.Arrays;

public enum RoleName {
    ROLE_ADMIN,
    ROLE_DOCTOR,
    ROLE_PATIENT,
    ROLE_STAFF;

    public static RoleName fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AuthValidationException("Vai trò người dùng không được để trống");
        }
        String normalized = raw.trim().toUpperCase();
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        final String target = normalized;
        return Arrays.stream(values())
                .filter(role -> role.name().equals(target))
                .findFirst()
                .orElseThrow(() -> new AuthValidationException(
                        "Vai trò không hợp lệ: " + raw + ". Chỉ hỗ trợ ROLE_ADMIN, ROLE_DOCTOR, ROLE_PATIENT, ROLE_STAFF"
                ));
    }
}

