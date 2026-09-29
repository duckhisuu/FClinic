package com.fclinic.patientservice.application.port.in;

import com.fclinic.patientservice.application.result.PatientView;
import java.util.List;

public interface ListPatientsUseCase {
    List<PatientView> listAll();
}
