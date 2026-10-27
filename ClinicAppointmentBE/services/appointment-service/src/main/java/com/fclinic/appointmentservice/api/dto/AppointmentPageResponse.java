package com.fclinic.appointmentservice.api.dto;

import com.fclinic.appointmentservice.application.result.AppointmentPageView;
import java.util.List;

public record AppointmentPageResponse(
    List<AppointmentResponse> content, int page, int size, long totalElements, long totalPages) {
  public static AppointmentPageResponse from(AppointmentPageView v) {
    return new AppointmentPageResponse(
        v.content().stream().map(AppointmentResponse::from).toList(),
        v.page(),
        v.size(),
        v.totalElements(),
        v.totalPages());
  }
}
