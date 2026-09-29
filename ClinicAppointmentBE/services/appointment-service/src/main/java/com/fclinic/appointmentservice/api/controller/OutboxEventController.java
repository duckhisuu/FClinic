package com.fclinic.appointmentservice.api.controller;

import com.fclinic.appointmentservice.api.dto.OutboxEventResponse;
import com.fclinic.appointmentservice.infrastructure.persistence.SpringDataJpaOutboxEventRepository;
import com.fclinic.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments/outbox")
@RequiredArgsConstructor
public class OutboxEventController {

    private final SpringDataJpaOutboxEventRepository outboxRepository;

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<OutboxEventResponse>>> getRecentOutboxEvents() {
        List<OutboxEventResponse> events = outboxRepository.findTop50ByOrderByCreatedAtDesc()
                .stream()
                .map(OutboxEventResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(events));
    }
}
