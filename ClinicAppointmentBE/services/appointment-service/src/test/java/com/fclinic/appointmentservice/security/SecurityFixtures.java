package com.fclinic.appointmentservice.security;

import com.fclinic.appointmentservice.application.AppointmentActor;
import com.fclinic.appointmentservice.config.AppointmentSecuritySettings;
import com.fclinic.appointmentservice.domain.aggregate.Appointment;
import com.fclinic.appointmentservice.domain.model.AppointmentStatus;
import com.fclinic.appointmentservice.domain.vo.BookingCode;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;

final class SecurityFixtures {
  static final UUID PATIENT = UUID.randomUUID(), OTHER = UUID.randomUUID(), DOCTOR = UUID.randomUUID(), ID = UUID.randomUUID();
  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);
  static AppointmentActor actor(String role, UUID patient, UUID doctor) {
    return new AppointmentActor("subject-" + role, "USER", Set.of(role), patient, doctor, null, System.nanoTime() + Duration.ofSeconds(5).toNanos());
  }
  static AppointmentActor alice() { return actor("ROLE_PATIENT", PATIENT, null); }
  static Appointment appointment() {
    return new Appointment(ID, BookingCode.generate(), PATIENT, "Synthetic", "+84900000001", DOCTOR, "Doctor", "General",
        UUID.randomUUID(), LocalDate.of(2026, 11, 20), LocalTime.of(9, 0), LocalTime.of(10, 0), "Private reason",
        BigDecimal.TEN, AppointmentStatus.CONFIRMED, 0, CLOCK.instant(), CLOCK.instant());
  }
  static AppointmentSecuritySettings settings(URI uri) {
    return new AppointmentSecuritySettings(uri, "https://identity.example.test", "appointment-service",
        "appointment-introspection", "identity-test-secret", uri, "appointment-scope", "scope-test-secret",
        "patient-reference", "patient-test-secret", "notification-reference", "notification-test-secret",
        null, "appointment-service", "token-test-secret");
  }
}
