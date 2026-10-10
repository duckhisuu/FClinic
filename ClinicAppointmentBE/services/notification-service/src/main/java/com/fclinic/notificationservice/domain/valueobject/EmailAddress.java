package com.fclinic.notificationservice.domain.valueobject;

import java.util.regex.Pattern;

public record EmailAddress(String value) {

    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public EmailAddress {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email address must not be blank");
        }
        String normalized = value.trim().toLowerCase();
        if (!SIMPLE_EMAIL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid email address: " + value);
        }
        value = normalized;
    }
}
