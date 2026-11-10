package com.fclinic.patientservice.application.usecase;

import com.fclinic.patientservice.application.port.out.*;
import com.fclinic.patientservice.application.security.PatientPolicy;
import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.domain.aggregate.Patient;
import com.fclinic.patientservice.domain.aggregate.MedicalRecord;
import com.fclinic.patientservice.domain.model.*;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.function.Supplier;
import static com.fclinic.patientservice.domain.model.PatientError.Code.*;

@Service
public class PatientApplicationService {
    private final PatientRepositoryPort repository;
    private final PatientWriter writer;
    private final PatientPolicy policy;
    private final DoctorClientPort doctors;
    private final AppointmentClientPort appointments;
    private final ReceptionAssignmentPort reception;
    public PatientApplicationService(PatientRepositoryPort repository, PatientWriter writer, PatientPolicy policy,
                                      DoctorClientPort doctors, AppointmentClientPort appointments, ReceptionAssignmentPort reception) {
        this.repository = repository; this.writer = writer; this.policy = policy;
        this.doctors = doctors; this.appointments = appointments;
        this.reception=reception;
    }
    public Patient create(TrustedCaller c, ProfileData data) {
        policy.createProfile(c);
        return c.userRole("ROLE_PATIENT") ? writer.createSelf(data, c.subject()) : writer.create(data, c.subject());
    }
    public Patient selfProfile(TrustedCaller c) {
        if (!c.active() || !c.userRole("ROLE_PATIENT")) throw new PatientError(FORBIDDEN);
        Patient patient = writer.selfProfile(c.subject());
        if (patient == null) throw new PatientError(PATIENT_NOT_FOUND);
        if (c.patientId() != null && !c.patientId().equals(patient.id())) throw new PatientError(FORBIDDEN);
        return patient;
    }
    public Patient get(TrustedCaller c, UUID id) {
        if(reception.enabled() && c.userRole("ROLE_STAFF")) {
            policy.receptionRead(c);
            Patient patient=parent(id);
            reception.assign(c,java.util.Set.of(id),"READ");
            policy.readProfile(c,id);
            return patient;
        }
        policy.readProfile(c, id);
        return parent(id);
    }
    public PageResult<Patient> receptionLookup(TrustedCaller c,SearchQuery query) {
        policy.receptionSearch(c);
        if(!reception.enabled())throw new PatientError(DEPENDENCY_UNAVAILABLE);
        if(query.size()>20 || (query.phoneNumber()==null && (query.keyword()==null || query.keyword().length()<2)))
            throw PatientError.invalid("keyword");
        var result=repository.receptionLookup(query);
        reception.assign(c,result.content().stream().map(Patient::id).collect(java.util.stream.Collectors.toSet()),"SEARCH");
        return result;
    }
    public PageResult<Patient> search(TrustedCaller c, SearchQuery query) {
        return repository.search(query, policy.search(c));
    }
    public Patient replace(TrustedCaller c, UUID id, long version, ProfileData data) {
        policy.replaceProfile(c, id);
        return writer.replace(id, version, data, c.subject());
    }
    public MedicalRecord createRecord(TrustedCaller c, UUID patientId, UUID doctorId, UUID appointmentId,
                                      ClinicalData data) {
        policy.createMedical(c, patientId, doctorId, appointmentId, data.visitDate());
        parent(patientId);
        // No write transaction exists while providers are called.
        var doctor = dependency(() -> doctors.find(doctorId));
        if (doctor == null) throw new PatientError(DEPENDENCY_UNAVAILABLE);
        var d = doctor.orElseThrow(() -> new PatientError(REFERENCE_MISMATCH));
        if (!doctorId.equals(d.id())) throw new PatientError(DEPENDENCY_UNAVAILABLE);
        if (!d.active()) throw new PatientError(REFERENCE_MISMATCH);
        if (appointmentId != null) {
            var appointment = dependency(() -> appointments.find(appointmentId));
            if (appointment == null) throw new PatientError(DEPENDENCY_UNAVAILABLE);
            var a = appointment.orElseThrow(() -> new PatientError(REFERENCE_MISMATCH));
            if (!appointmentId.equals(a.id()) || a.patientId() == null || a.doctorId() == null)
                throw new PatientError(DEPENDENCY_UNAVAILABLE);
            if (a.cancelled() || !a.patientId().equals(patientId) || !doctorId.equals(a.doctorId()))
                throw new PatientError(REFERENCE_MISMATCH);
        }
        return writer.createRecord(patientId, doctorId, appointmentId, data, c.subject());
    }
    public PageResult<MedicalRecord> records(TrustedCaller c, UUID patientId, SearchQuery query) {
        policy.readMedical(c, patientId);
        parent(patientId);
        return repository.records(patientId, query);
    }
    public MedicalRecord record(TrustedCaller c, UUID patientId, UUID recordId) {
        policy.readMedical(c, patientId);
        parent(patientId);
        return repository.findRecord(patientId, recordId)
                .orElseThrow(() -> new PatientError(MEDICAL_RECORD_NOT_FOUND));
    }
    public MedicalRecord replaceRecord(TrustedCaller c, UUID patientId, UUID recordId, long version,
                                       ClinicalData data, String reason) {
        policy.editMedical(c, patientId);
        return writer.replaceRecord(patientId, recordId, version, data, reason, c.subject(), c.doctorId());
    }
    private Patient parent(UUID id) {
        return repository.find(id).orElseThrow(() -> new PatientError(PATIENT_NOT_FOUND));
    }
    private static <T> T dependency(Supplier<T> call) {
        try { return call.get(); }
        catch (RuntimeException ex) { throw new PatientError(DEPENDENCY_UNAVAILABLE); }
    }
}
