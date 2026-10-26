package com.fclinic.patientservice.domain.model;
public record SearchQuery(String keyword, String phoneNumber, Long appointmentId, int page, int size,
                          String sortField, boolean ascending) { }
