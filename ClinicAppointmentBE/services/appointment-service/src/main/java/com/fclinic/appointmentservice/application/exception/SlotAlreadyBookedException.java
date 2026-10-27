package com.fclinic.appointmentservice.application.exception;

import com.fclinic.appointmentservice.domain.AppointmentError;

public class SlotAlreadyBookedException extends AppointmentError {
  public SlotAlreadyBookedException() {
    super(409, "SLOT_ALREADY_BOOKED", "Khung giờ vừa được người khác đặt. Vui lòng chọn giờ khác.");
  }
}
