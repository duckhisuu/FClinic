package com.fclinic.authservice.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateUserRequest(
        @NotBlank(message = "Tên đăng nhập không được để trống")
        @Size(min = 3, max = 50, message = "Tên đăng nhập phải từ 3 đến 50 ký tự")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Tên đăng nhập chỉ được chứa chữ cái, số, dấu chấm, gạch dưới và gạch nối")
        String username,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 6, max = 100, message = "Mật khẩu phải từ 6 đến 100 ký tự")
        String password,

        @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 100, message = "Họ và tên tối đa 100 ký tự")
        String fullName,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        String email,

        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phoneNumber,

        @NotBlank(message = "Vai trò không được để trống")
        String role,

        UUID patientId,
        UUID doctorId
) {
    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + ", role=" + role + "]";
    }
}

