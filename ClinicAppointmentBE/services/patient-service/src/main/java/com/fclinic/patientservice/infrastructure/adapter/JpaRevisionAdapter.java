package com.fclinic.patientservice.infrastructure.adapter;

import com.fclinic.patientservice.application.port.out.RevisionPort;
import com.fclinic.patientservice.domain.aggregate.Patient;
import com.fclinic.patientservice.domain.aggregate.MedicalRecord;
import com.fclinic.patientservice.infrastructure.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
@Transactional(propagation=Propagation.MANDATORY)
public class JpaRevisionAdapter implements RevisionPort {
    @PersistenceContext private EntityManager em;
    private final ObjectMapper json;
    public JpaRevisionAdapter(ObjectMapper json) { this.json = json; }
    public void patient(Patient before, Patient after) {
        var revision = new JpaPatientRevisionEntity();
        revision.setPatientId(after.id()); revision.setEntityVersion(after.version());
        revision.setAction(before == null ? "CREATE" : "UPDATE");
        revision.setBeforeData(before == null ? null : json.writeValueAsString(before));
        revision.setAfterData(json.writeValueAsString(after));
        revision.setActorId(after.updatedBy()); revision.setChangedAt(after.updatedAt());
        em.persist(revision); em.flush();
    }
    public void medical(MedicalRecord before, MedicalRecord after, String reason) {
        var revision = new JpaMedicalRevisionEntity();
        revision.setMedicalRecordId(after.id()); revision.setEntityVersion(after.version());
        revision.setAction(before == null ? "CREATE" : "UPDATE");
        revision.setBeforeData(before == null ? null : json.writeValueAsString(before));
        revision.setAfterData(json.writeValueAsString(after));
        revision.setActorId(after.updatedBy()); revision.setChangedAt(after.updatedAt()); revision.setChangeReason(reason);
        em.persist(revision); em.flush();
    }
}
