package com.fclinic.patientservice.application.port.out;
import com.fclinic.patientservice.domain.aggregate.Patient;
import com.fclinic.patientservice.domain.aggregate.MedicalRecord;
public interface RevisionPort {
    void patient(Patient before, Patient after);
    void medical(MedicalRecord before, MedicalRecord after, String reason);
}
