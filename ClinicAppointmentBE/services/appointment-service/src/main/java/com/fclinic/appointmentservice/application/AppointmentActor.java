package com.fclinic.appointmentservice.application;

/**
 * Audit/idempotency context supplied by the existing container principal. No authentication here.
 */
public record AppointmentActor(String subject, long deadlineNanos) {}
