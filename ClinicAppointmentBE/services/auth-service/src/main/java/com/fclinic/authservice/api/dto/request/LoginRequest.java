package com.fclinic.authservice.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Tên đăng nhập hoặc email không được để trống")
        String username,

        @NotBlank(message = "Mật khẩu không được để trống")
        String password
) {
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + "]";
    }
}

