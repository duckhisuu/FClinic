package com.fclinic.patientservice.application.port.in;

import com.fclinic.patientservice.application.command.RegisterPatientCommand;
import com.fclinic.patientservice.application.result.PatientView;

public interface RegisterPatientUseCase {
    PatientView registerOrUpdate(RegisterPatientCommand command);
}
