package com.fclinic.authservice.application.service;

import com.fclinic.authservice.application.port.out.UserRepositoryPort;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.util.UuidV4;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only account mapping. Never joins accounts by phone/email or writes Patient storage. */
@Service
public class AppointmentRecipientService {
    private final UserRepositoryPort users;
    public AppointmentRecipientService(UserRepositoryPort users) { this.users = users; }
    public record DoctorAccount(UUID userId, String name, String email) { }
    public record Accounts(UUID patientId, UUID doctorId, UUID patientUserId, DoctorAccount doctor) { }
    public static class UnresolvedMapping extends RuntimeException { }

    @Transactional(readOnly = true)
    public Accounts resolve(UUID patientId, UUID doctorId) {
        UuidV4.requireValid(patientId, "patientId"); UuidV4.requireValid(doctorId, "doctorId");
        List<User> patients = users.findPatientAccounts(patientId);
        List<User> doctors = users.findDoctorAccounts(doctorId);
        if (patients.size() > 1 || doctors.size() != 1) throw new UnresolvedMapping();
        User doctor = doctors.getFirst();
        if (!doctor.isActive() || doctor.getRoleName() != com.fclinic.authservice.domain.valueobject.RoleName.ROLE_DOCTOR
                || !doctorId.equals(doctor.getDoctorId())) throw new UnresolvedMapping();
        User patient = patients.isEmpty() ? null : patients.getFirst();
        if (patient != null && (!patient.isActive() || patient.getRoleName() != com.fclinic.authservice.domain.valueobject.RoleName.ROLE_PATIENT
                || !patientId.equals(patient.getPatientId()))) throw new UnresolvedMapping();
        return new Accounts(patientId, doctorId, patient == null ? null : patient.getId(),
                new DoctorAccount(doctor.getId(), doctor.getFullName(), doctor.getEmail()));
    }
}
