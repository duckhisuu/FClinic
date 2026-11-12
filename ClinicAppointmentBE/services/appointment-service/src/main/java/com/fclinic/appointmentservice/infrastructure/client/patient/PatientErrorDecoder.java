package com.fclinic.appointmentservice.infrastructure.client.patient;

import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.infrastructure.client.config.UnavailableErrorDecoder;
import feign.Response;

public class PatientErrorDecoder extends UnavailableErrorDecoder {
  @Override public Exception decode(String key, Response response) {
    return response.status() == 404 ? new AppointmentError(404, "PATIENT_NOT_FOUND", "Không tìm thấy bệnh nhân.") : super.decode(key, response);
  }
}

