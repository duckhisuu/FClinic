package com.fclinic.appointmentservice.application.usecase;

import com.fclinic.appointmentservice.application.port.out.DoctorPatientAssignmentRepository;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.domain.vo.BusinessId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorPatientAssignmentService {
    private final DoctorPatientAssignmentRepository repository;
    public DoctorPatientAssignmentService(DoctorPatientAssignmentRepository repository) { this.repository = repository; }
    @Transactional(readOnly = true)
    public List<UUID> assignedPatients(UUID doctorId) {
        BusinessId.require(doctorId);
        var patients = repository.assignedPatients(doctorId);
        if (patients.size() > 1000) throw AppointmentError.unavailable();
        return patients;
    }
}
