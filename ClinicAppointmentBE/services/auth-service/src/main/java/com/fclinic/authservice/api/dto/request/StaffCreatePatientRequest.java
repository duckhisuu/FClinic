package com.fclinic.authservice.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StaffCreatePatientRequest(
        @NotBlank(message = "Họ và tên bệnh nhân không được để trống")
        @Size(max = 100, message = "Họ và tên tối đa 100 ký tự")
        String fullName,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phoneNumber,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        String email,

        @Size(min = 6, max = 100, message = "Mật khẩu khởi tạo phải từ 6 đến 100 ký tự")
        String initialPassword,

        UUID patientId
) {
    @Override
    public String toString() {
        return "StaffCreatePatientRequest[email=" + email + ", phoneNumber=" + phoneNumber + "]";
    }
}

