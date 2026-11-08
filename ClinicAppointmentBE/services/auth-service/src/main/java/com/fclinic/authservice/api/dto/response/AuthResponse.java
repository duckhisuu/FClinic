package com.fclinic.authservice.api.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
    @Override
    public String toString() {
        return "AuthResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", userId="
                + (user != null ? user.id() : null) + "]";
    }
}

