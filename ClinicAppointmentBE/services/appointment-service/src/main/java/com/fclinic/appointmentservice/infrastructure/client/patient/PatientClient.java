package com.fclinic.appointmentservice.infrastructure.client.patient;

import com.fclinic.appointmentservice.infrastructure.client.config.ProviderResponse;
import com.fclinic.appointmentservice.infrastructure.client.patient.dto.PatientProfileDto;
import feign.Request;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "patient-service", contextId = "appointmentPatient", url = "${appointment.patient-url:}", configuration = PatientFeignConfig.class)
public interface PatientClient {
  @GetMapping("/api/v3/patients/{patientId}")
  ProviderResponse<PatientProfileDto> getPatient(@PathVariable("patientId") UUID id,
      @RequestHeader("Authorization") String machineBearer, Request.Options options);
}

