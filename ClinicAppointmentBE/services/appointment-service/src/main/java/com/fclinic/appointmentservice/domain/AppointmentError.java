package com.fclinic.appointmentservice.domain;

public class AppointmentError extends RuntimeException {
  private final int status;
  private final String code;

  public AppointmentError(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static AppointmentError validation(String message) {
    return new AppointmentError(400, "VALIDATION_ERROR", message);
  }

  public static AppointmentError unavailable() {
    return new AppointmentError(
        503,
        "DEPENDENCY_UNAVAILABLE",
        "Chưa thể xác minh với dịch vụ liên quan. Vui lòng thử lại.");
  }

  public static AppointmentError missing() {
    return new AppointmentError(404, "APPOINTMENT_NOT_FOUND", "Không tìm thấy lịch hẹn.");
  }

  public static AppointmentError conflict(String code, String message) {
    return new AppointmentError(409, code, message);
  }
}
