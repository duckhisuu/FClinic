package com.fclinic.appointmentservice.application.port.in;

import com.fclinic.appointmentservice.application.command.BookAppointmentCommand;
import com.fclinic.appointmentservice.application.result.AppointmentView;

public interface BookAppointmentUseCase {
    AppointmentView bookAppointment(BookAppointmentCommand command);
}
