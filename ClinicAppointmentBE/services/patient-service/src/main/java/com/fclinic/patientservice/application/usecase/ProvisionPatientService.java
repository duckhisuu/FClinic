package com.fclinic.patientservice.application.usecase;

import com.fclinic.patientservice.application.port.out.*;
import com.fclinic.patientservice.domain.aggregate.Patient;
import com.fclinic.patientservice.domain.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.fclinic.patientservice.domain.model.PatientError.Code.*;

/** Only the authenticated Auth internal controller may invoke this provisioning boundary. */
@Service
public class ProvisionPatientService {
    public record Proof(UUID accountId, UUID patientId, String subject, String requestHash, String status) { }
    private final JdbcTemplate jdbc;
    private final PatientRepositoryPort patients;
    private final RevisionPort revisions;
    private final Clock clock;
    public ProvisionPatientService(JdbcTemplate jdbc, PatientRepositoryPort patients, RevisionPort revisions, Clock clock) {
        this.jdbc=jdbc; this.patients=patients; this.revisions=revisions; this.clock=clock;
    }
    @Transactional
    public Proof ensure(UUID account, UUID patientId, String subject, String hash, ProfileData profile) {
        // Account lock exists even before the first mapping row. Hash collisions only serialize extra work.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "auth-patient-onboarding:"+account);
        var mappings=jdbc.queryForList("SELECT patient_id,subject,request_hash FROM patient_onboardings WHERE account_id=?",account);
        Patient patient;
        if (!mappings.isEmpty()) {
            var mapping=mappings.getFirst();
            if (!patientId.equals(mapping.get("patient_id")) || !subject.equals(mapping.get("subject"))
                    || !hash.equals(mapping.get("request_hash"))) throw new PatientError(REFERENCE_MISMATCH);
            patient=patients.lock(patientId).orElseThrow(()->new PatientError(REFERENCE_MISMATCH));
            if (!subject.equals(patient.createdBy())) throw new PatientError(REFERENCE_MISMATCH);
        } else {
            if (patients.find(patientId).isPresent()) throw new PatientError(REFERENCE_MISMATCH);
            var now=clock.instant().truncatedTo(ChronoUnit.MICROS);
            patient=patients.insert(new Patient(patientId,"PAT-"+patientId,profile.fullName(),profile.phoneNumber(),profile.email(),
                profile.dateOfBirth(),profile.gender(),profile.address(),ProfileData.Status.ACTIVE,0,now,now,subject,subject));
            jdbc.update("INSERT INTO patient_onboardings(account_id,patient_id,subject,request_hash) VALUES(?,?,?,?)",
                account,patientId,subject,hash);
            revisions.patient(null,patient);
        }
        if (patient.status()!=ProfileData.Status.ACTIVE) throw new PatientError(PATIENT_INACTIVE);
        return new Proof(account,patientId,subject,hash,"ACTIVE");
    }
}
