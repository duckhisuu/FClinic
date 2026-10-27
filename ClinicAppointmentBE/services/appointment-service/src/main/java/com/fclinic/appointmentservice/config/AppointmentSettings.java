package com.fclinic.appointmentservice.config;

import java.time.Duration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AppointmentSettings {
  private final Environment env;

  public AppointmentSettings(Environment env) {
    this.env = env;
    Duration lead = duration("reminder-lead", null);
    if (lead != null && (lead.isNegative() || lead.isZero())) {
      throw new IllegalArgumentException("Appointment reminder lead must be positive");
    }
  }

  public String value(String key) {
    return env.getProperty("appointment." + key, "").trim();
  }

  public Duration duration(String key, Duration fallback) {
    String s = value(key);
    return s.isEmpty() ? fallback : Duration.parse(s);
  }

  public String patientUrl() {
    return value("patient-url");
  }

  public String doctorUrl() {
    return value("doctor-url");
  }

  public String serviceToken() {
    return value("service-token");
  }
}
