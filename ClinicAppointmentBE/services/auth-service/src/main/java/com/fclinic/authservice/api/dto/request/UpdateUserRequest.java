package com.fclinic.authservice.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateUserRequest(
        @Size(max = 100, message = "Họ và tên tối đa 100 ký tự")
        String fullName,

        @Email(message = "Email không đúng định dạng")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        String email,

        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phoneNumber,

        String role,
        Boolean active,
        UUID patientId,
        UUID doctorId
) {
}

