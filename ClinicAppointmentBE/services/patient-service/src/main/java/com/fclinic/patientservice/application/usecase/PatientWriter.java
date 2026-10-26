package com.fclinic.patientservice.application.usecase;

import com.fclinic.patientservice.application.port.out.PatientRepositoryPort;
import com.fclinic.patientservice.application.port.out.RevisionPort;
import com.fclinic.patientservice.domain.aggregate.Patient;
import com.fclinic.patientservice.domain.aggregate.MedicalRecord;
import com.fclinic.patientservice.domain.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import static com.fclinic.patientservice.domain.model.PatientError.Code.*;

@Service
public class PatientWriter {
    private final PatientRepositoryPort repository;
    private final RevisionPort revisions;
    private final Clock clock;
    public PatientWriter(PatientRepositoryPort repository, RevisionPort revisions, Clock clock) {
        this.repository = repository; this.revisions = revisions; this.clock = clock;
    }
    @Transactional
    public Patient create(ProfileData data, String actor) {
        Patient saved = repository.insert(Patient.create(data, actor, clock.instant().truncatedTo(ChronoUnit.MICROS)));
        revisions.patient(null, saved);
        return saved;
    }
    @Transactional
    public Patient replace(UUID id, long version, ProfileData data, String actor) {
        // Also serializes deactivation against medical create; optimistic validation remains mandatory.
        Patient before = repository.lock(id).orElseThrow(() -> new PatientError(PATIENT_NOT_FOUND));
        PatientError.checkVersion(version, before.version());
        if (before.profile().equals(data)) {
            repository.checkAtCommit(before);
            return before;
        }
        Patient saved = repository.update(before.replace(data, actor, clock.instant().truncatedTo(ChronoUnit.MICROS)));
        revisions.patient(before, saved);
        return saved;
    }
    @Transactional
    public MedicalRecord createRecord(UUID patientId, long doctorId, Long appointmentId,
                                      ClinicalData data, String actor) {
        Patient parent = repository.lock(patientId).orElseThrow(() -> new PatientError(PATIENT_NOT_FOUND));
        if (parent.status() != ProfileData.Status.ACTIVE) throw new PatientError(PATIENT_INACTIVE);
        MedicalRecord saved = repository.insertRecord(
                MedicalRecord.create(patientId, doctorId, appointmentId, data, actor, clock.instant().truncatedTo(ChronoUnit.MICROS)));
        revisions.medical(null, saved, null);
        return saved;
    }
    @Transactional
    public MedicalRecord replaceRecord(UUID patientId, long id, long version, ClinicalData data,
                                       String reason, String actor, long doctorId) {
        if (reason == null || reason.isBlank() || reason.length() > 1000) throw PatientError.invalid("changeReason");
        repository.find(patientId).orElseThrow(() -> new PatientError(PATIENT_NOT_FOUND));
        MedicalRecord before = repository.findRecord(patientId, id)
                .orElseThrow(() -> new PatientError(MEDICAL_RECORD_NOT_FOUND));
        // Both immutable author subject and doctor identity must match.
        if (!before.createdBy().equals(actor) || before.doctorId() != doctorId) throw new PatientError(FORBIDDEN);
        PatientError.checkVersion(version, before.version());
        if (before.clinical().equals(data)) {
            repository.checkAtCommit(before);
            return before;
        }
        MedicalRecord saved = repository.updateRecord(before.replace(data, actor, clock.instant().truncatedTo(ChronoUnit.MICROS)));
        revisions.medical(before, saved, reason.strip());
        return saved;
    }
}
