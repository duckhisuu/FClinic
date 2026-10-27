package com.fclinic.appointmentservice.api.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Original v1 controller path is retained only to report its retirement; no outbox data is exposed.
 */
@RestController
@RequestMapping("/api/v1/appointments/outbox")
public class OutboxEventController {
  @GetMapping
  public ResponseEntity<?> getOutboxEvents() {
    return ResponseEntity.status(410)
        .body(
            Map.of(
                "status",
                410,
                "message",
                "Outbox là hạ tầng nội bộ; API monitor v1 đã ngừng sử dụng."));
  }
}
