package com.fclinic.appointmentservice.application.port.in;

import com.fclinic.appointmentservice.application.result.AppointmentView;

import java.util.Optional;

public interface GetAppointmentUseCase {
    Optional<AppointmentView> getById(Long id);
    Optional<AppointmentView> getByBookingCode(String bookingCode);
}
