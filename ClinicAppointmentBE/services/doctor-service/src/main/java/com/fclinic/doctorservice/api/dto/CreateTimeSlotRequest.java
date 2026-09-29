package com.fclinic.doctorservice.api.dto;

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
public class CreateTimeSlotRequest {

    @NotNull(message = "Bắt buộc có ngày khám")
    private LocalDate slotDate;

    @NotNull(message = "Bắt buộc có giờ bắt đầu")
    private LocalTime startTime;

    @NotNull(message = "Bắt buộc có giờ kết thúc")
    private LocalTime endTime;
}
