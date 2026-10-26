package com.fclinic.patientservice.application.port.out;
import com.fclinic.patientservice.domain.aggregate.Patient;
import com.fclinic.patientservice.domain.aggregate.MedicalRecord;
import com.fclinic.patientservice.domain.model.PageResult;
import com.fclinic.patientservice.domain.model.SearchQuery;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PatientRepositoryPort {
    Optional<Patient> find(UUID id);
    Optional<Patient> lock(UUID id);
    Patient insert(Patient patient);
    Patient update(Patient patient);
    void checkAtCommit(Patient patient);
    PageResult<Patient> search(SearchQuery query, Set<UUID> allowedPatients);
    Optional<MedicalRecord> findRecord(UUID patientId, long recordId);
    MedicalRecord insertRecord(MedicalRecord record);
    MedicalRecord updateRecord(MedicalRecord record);
    void checkAtCommit(MedicalRecord record);
    PageResult<MedicalRecord> records(UUID patientId, SearchQuery query);
}
