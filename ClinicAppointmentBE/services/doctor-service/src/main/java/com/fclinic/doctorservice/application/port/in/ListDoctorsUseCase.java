package com.fclinic.doctorservice.application.port.in;

import com.fclinic.doctorservice.application.result.DoctorView;
import java.util.List;

public interface ListDoctorsUseCase {
    List<DoctorView> listDoctors(String specialty, String department);
}
