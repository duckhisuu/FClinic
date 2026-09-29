package com.fclinic.appointmentservice.application.port.in;

import com.fclinic.appointmentservice.application.command.CancelAppointmentCommand;
import com.fclinic.appointmentservice.application.result.AppointmentView;

public interface CancelAppointmentUseCase {
    AppointmentView cancelAppointment(CancelAppointmentCommand command);
}
