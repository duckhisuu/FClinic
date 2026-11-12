package com.fclinic.appointmentservice.infrastructure.client.auth.dto;

public record RecipientAccountsDto(String patientId, String doctorId, String patientUserId, DoctorAccount doctor) {
  public record DoctorAccount(String userId, String name, String email) {}
}

