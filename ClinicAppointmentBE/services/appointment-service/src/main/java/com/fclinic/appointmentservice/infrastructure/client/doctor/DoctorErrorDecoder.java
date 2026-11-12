package com.fclinic.appointmentservice.infrastructure.client.doctor;

import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.infrastructure.client.config.UnavailableErrorDecoder;
import feign.Response;

public class DoctorErrorDecoder extends UnavailableErrorDecoder {
  @Override public Exception decode(String key, Response response) {
    if (response.status() != 404) return super.decode(key, response);
    return key.contains("#getSlot(")
        ? AppointmentError.conflict("SLOT_NOT_BOOKABLE", "Khung giờ không tồn tại hoặc không thuộc bác sĩ đã chọn.")
        : new AppointmentError(404, "DOCTOR_NOT_FOUND", "Không tìm thấy bác sĩ.");
  }
}

