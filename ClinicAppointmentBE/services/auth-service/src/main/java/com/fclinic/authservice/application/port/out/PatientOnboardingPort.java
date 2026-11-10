package com.fclinic.authservice.application.port.out;

import java.util.*;

public interface PatientOnboardingPort {
    record Reservation(UUID userId, UUID patientId, UUID subject, String requestBody,
                       String commandBody, String requestHash) { }
    Optional<Reservation> find(UUID user);
    Reservation reserve(UUID user, UUID subject, String command, String body, String hash);
    void bind(Reservation reservation);
}
