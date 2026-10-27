package com.fclinic.appointmentservice.application.exception;

import com.fclinic.appointmentservice.domain.AppointmentError;

public class AppointmentNotFoundException extends AppointmentError {
  public AppointmentNotFoundException() {
    super(404, "APPOINTMENT_NOT_FOUND", "Không tìm thấy lịch hẹn.");
  }
}
