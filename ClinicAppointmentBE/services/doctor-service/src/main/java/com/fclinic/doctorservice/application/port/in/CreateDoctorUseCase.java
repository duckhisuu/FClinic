package com.fclinic.doctorservice.application.port.in;

import com.fclinic.doctorservice.application.command.CreateDoctorCommand;
import com.fclinic.doctorservice.application.result.DoctorView;

public interface CreateDoctorUseCase {
    DoctorView createDoctor(CreateDoctorCommand command);
}
