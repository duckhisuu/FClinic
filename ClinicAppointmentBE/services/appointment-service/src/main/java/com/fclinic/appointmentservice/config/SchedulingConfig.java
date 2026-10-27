package com.fclinic.appointmentservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
  @org.springframework.context.annotation.Bean
  public com.fclinic.appointmentservice.application.AppointmentSchedulingPolicy
      appointmentSchedulingPolicy(AppointmentSettings settings) {
    return new com.fclinic.appointmentservice.application.AppointmentSchedulingPolicy(
        Boolean.parseBoolean(settings.value("outbox-enabled")),
        settings.duration("reminder-lead", null));
  }
}
