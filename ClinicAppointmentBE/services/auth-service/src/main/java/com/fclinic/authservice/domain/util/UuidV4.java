package com.fclinic.authservice.domain.util;

import com.fclinic.authservice.domain.exception.AuthValidationException;

import java.util.UUID;
import java.util.regex.Pattern;

public final class UuidV4 {

    private static final Pattern CANONICAL_UUID_V4 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
    );

    private UuidV4() {
    }

    public static UUID generate() {
        return UUID.randomUUID();
    }

    public static UUID requireValid(UUID value, String fieldName) {
        if (value == null || !isUuidV4(value)) {
            throw new AuthValidationException(fieldName + " phải là UUIDv4 hợp lệ");
        }
        return value;
    }

    public static UUID requireValidNullable(UUID value, String fieldName) {
        if (value == null) {
            return null;
        }
        return requireValid(value, fieldName);
    }

    public static UUID parse(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new AuthValidationException(fieldName + " không được để trống");
        }
        String normalized = raw.trim().toLowerCase();
        if (!CANONICAL_UUID_V4.matcher(normalized).matches()) {
            throw new AuthValidationException(fieldName + " phải đúng định dạng UUIDv4");
        }
        return UUID.fromString(normalized);
    }

    public static UUID parseNullable(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parse(raw, fieldName);
    }

    public static boolean isUuidV4(UUID value) {
        if (value == null) {
            return false;
        }
        return value.version() == 4
                && value.variant() == 2
                && CANONICAL_UUID_V4.matcher(value.toString()).matches();
    }

    public static boolean isUuidV4String(String raw) {
        if (raw == null) {
            return false;
        }
        return CANONICAL_UUID_V4.matcher(raw.trim().toLowerCase()).matches();
    }
}

