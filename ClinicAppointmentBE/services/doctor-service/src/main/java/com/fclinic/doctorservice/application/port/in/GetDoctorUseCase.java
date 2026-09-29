package com.fclinic.doctorservice.application.port.in;

import com.fclinic.doctorservice.application.result.DoctorView;
import java.util.Optional;

public interface GetDoctorUseCase {
    Optional<DoctorView> getById(Long id);
}
