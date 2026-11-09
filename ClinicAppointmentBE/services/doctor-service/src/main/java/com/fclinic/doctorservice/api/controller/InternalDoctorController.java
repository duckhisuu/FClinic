package com.fclinic.doctorservice.api.controller;

import com.fclinic.common.web.ApiResponse;
import com.fclinic.doctorservice.api.dto.PublishedSlotResponse;
import com.fclinic.doctorservice.application.exception.DoctorNotFoundException;
import com.fclinic.doctorservice.application.port.in.GetPublishedSlotUseCase;
import com.fclinic.doctorservice.application.result.TimeSlotView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/doctors")
@RequiredArgsConstructor
public class InternalDoctorController {

    private final GetPublishedSlotUseCase getPublishedSlotUseCase;

    @GetMapping("/{doctorId}/slots/{slotId}")
    public ResponseEntity<ApiResponse<PublishedSlotResponse>> getPublishedSlot(
            @PathVariable UUID doctorId,
            @PathVariable UUID slotId
    ) {
        TimeSlotView view = getPublishedSlotUseCase.getPublishedSlot(doctorId, slotId)
                .orElseThrow(() -> new DoctorNotFoundException("Không tìm thấy khung giờ hoặc không thuộc bác sĩ đã chọn"));
        PublishedSlotResponse response = new PublishedSlotResponse(
                view.id(),
                view.doctorId(),
                view.slotDate(),
                view.startTime(),
                view.endTime(),
                view.published()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
