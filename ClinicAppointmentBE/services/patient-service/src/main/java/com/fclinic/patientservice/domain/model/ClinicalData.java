package com.fclinic.patientservice.domain.model;
import java.time.Instant;
public record ClinicalData(Instant visitDate, String symptoms, String diagnosis, String treatment, String notes) { }
