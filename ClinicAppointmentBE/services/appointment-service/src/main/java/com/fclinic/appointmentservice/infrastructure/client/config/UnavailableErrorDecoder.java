package com.fclinic.appointmentservice.infrastructure.client.config;

import com.fclinic.appointmentservice.domain.AppointmentError;
import feign.Response;
import feign.codec.ErrorDecoder;

public class UnavailableErrorDecoder implements ErrorDecoder {
  public Exception decode(String key, Response response) { return AppointmentError.unavailable(); }
}

