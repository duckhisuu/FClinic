package com.fclinic.doctorservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDoctorRequest {

    @NotBlank(message = "Bắt buộc có tên bác sĩ")
    private String name;

    @NotBlank(message = "Bắt buộc có chuyên khoa")
    private String specialty;

    @NotBlank(message = "Bắt buộc có khoa phòng")
    private String department;

    private String qualification;

    @PositiveOrZero(message = "Số năm kinh nghiệm phải không âm")
    private Integer experienceYears;

    @NotNull(message = "Bắt buộc có phí khám")
    @PositiveOrZero(message = "Phí khám phải không âm")
    private Double consultationFee;

    private String roomNumber;

    private String bio;

    private String avatarUrl;
}
