package com.fclinic.patientservice.domain.model;
import java.util.UUID;
public record SearchQuery(String keyword, String phoneNumber, UUID appointmentId, int page, int size,
                          String sortField, boolean ascending) { }
