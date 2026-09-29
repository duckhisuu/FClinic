package com.fclinic.appointmentservice.application.port.in;

import com.fclinic.appointmentservice.application.result.AppointmentView;

import java.util.List;

public interface ListAppointmentsUseCase {
    List<AppointmentView> getAll();
    List<AppointmentView> getByPatientId(Long patientId);
    List<AppointmentView> getByPatientPhone(String phone);
}
