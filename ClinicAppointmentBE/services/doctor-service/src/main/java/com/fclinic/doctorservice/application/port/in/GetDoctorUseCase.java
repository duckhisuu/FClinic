package com.fclinic.doctorservice.application.port.in;

import com.fclinic.doctorservice.application.result.DoctorView;
import java.util.Optional;
import java.util.UUID;

public interface GetDoctorUseCase {
    Optional<DoctorView> getById(UUID id);
}
