package com.fclinic.patientservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePatientRequest {

    @NotBlank(message = "Bắt buộc có họ và tên")
    private String fullName;

    @NotBlank(message = "Bắt buộc có số điện thoại")
    private String phoneNumber;

    private String email;

    private LocalDate dateOfBirth;

    private String gender;

    private String address;
}
