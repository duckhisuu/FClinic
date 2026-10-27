package com.fclinic.appointmentservice.config;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class AppointmentConfig {
  @Bean
  public Clock appointmentClock() {
    return Clock.systemUTC();
  }
}
