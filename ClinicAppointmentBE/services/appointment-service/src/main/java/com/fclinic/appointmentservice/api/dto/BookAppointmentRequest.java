package com.fclinic.appointmentservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookAppointmentRequest {

    @NotNull(message = "Bắt buộc có patientId")
    private Long patientId;

    @NotBlank(message = "Bắt buộc có tên bệnh nhân")
    private String patientName;

    @NotBlank(message = "Bắt buộc có số điện thoại bệnh nhân")
    private String patientPhone;

    @NotNull(message = "Bắt buộc có doctorId")
    private Long doctorId;

    @NotBlank(message = "Bắt buộc có tên bác sĩ")
    private String doctorName;

    @NotBlank(message = "Bắt buộc có chuyên khoa bác sĩ")
    private String doctorSpecialty;

    @NotNull(message = "Bắt buộc có slotId")
    private Long slotId;

    @NotNull(message = "Bắt buộc có ngày hẹn khám")
    private LocalDate appointmentDate;

    @NotNull(message = "Bắt buộc có giờ bắt đầu")
    private LocalTime startTime;

    @NotNull(message = "Bắt buộc có giờ kết thúc")
    private LocalTime endTime;

    private String reasonForVisit;

    private Double consultationFee;
}
