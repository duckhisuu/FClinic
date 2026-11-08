package com.fclinic.authservice.application.model;

import com.fclinic.authservice.domain.aggregate.User;

public record AuthResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        User user
) {
    @Override
    public String toString() {
        return "AuthResult[userId=" + (user != null ? user.getId() : null) + ", expiresIn=" + expiresIn + "]";
    }
}

