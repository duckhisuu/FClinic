package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fclinic.appointmentservice.application.port.out.DoctorPatientAssignmentRepository;
import com.fclinic.appointmentservice.infrastructure.persistence.SpringDataJpaAppointmentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class JpaDoctorPatientAssignmentAdapter implements DoctorPatientAssignmentRepository {
    private final SpringDataJpaAppointmentRepository repository;
    public JpaDoctorPatientAssignmentAdapter(SpringDataJpaAppointmentRepository repository) { this.repository = repository; }
    public List<UUID> assignedPatients(UUID doctorId) { return repository.assignedPatients(doctorId, PageRequest.of(0, 1001)); }
}
