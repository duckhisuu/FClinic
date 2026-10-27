package com.fclinic.appointmentservice.application;

import java.time.Duration;

public record AppointmentSchedulingPolicy(boolean outboxEnabled, Duration reminderLead) {}
