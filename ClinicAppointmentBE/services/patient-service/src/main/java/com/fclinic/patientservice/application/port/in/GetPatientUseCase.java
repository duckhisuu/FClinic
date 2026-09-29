package com.fclinic.patientservice.application.port.in;

import com.fclinic.patientservice.application.result.PatientView;
import java.util.Optional;

public interface GetPatientUseCase {
    Optional<PatientView> getById(Long id);
    Optional<PatientView> getByPhoneNumber(String phoneNumber);
}
