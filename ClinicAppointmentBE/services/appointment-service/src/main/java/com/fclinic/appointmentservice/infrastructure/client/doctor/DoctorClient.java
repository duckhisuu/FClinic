package com.fclinic.appointmentservice.infrastructure.client.doctor;

import com.fclinic.appointmentservice.infrastructure.client.config.ProviderResponse;
import com.fclinic.appointmentservice.infrastructure.client.doctor.dto.*;
import feign.Request;
import java.util.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "doctor-service", contextId = "appointmentDoctor", url = "${appointment.doctor-url:}", configuration = DoctorFeignConfig.class)
public interface DoctorClient {
  @GetMapping("/internal/v1/doctors/{doctorId}")
  ResponseEntity<ProviderResponse<DoctorReferenceDto>> getDoctor(@PathVariable("doctorId") UUID id, Request.Options options);
  @GetMapping("/internal/v1/doctors/{doctorId}/slots/{slotId}")
  ResponseEntity<ProviderResponse<DoctorSlotDto>> getSlot(@PathVariable("doctorId") UUID doctor,
      @PathVariable("slotId") UUID slot, Request.Options options);
  @GetMapping("/internal/v1/doctors/{doctorId}/slots")
  ResponseEntity<ProviderResponse<List<DoctorSlotDto>>> getSlots(@PathVariable("doctorId") UUID doctor,
      @RequestParam("date") String date, Request.Options options);
}

