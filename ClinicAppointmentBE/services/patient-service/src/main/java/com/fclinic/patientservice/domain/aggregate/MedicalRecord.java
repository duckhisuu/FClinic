package com.fclinic.patientservice.domain.aggregate;
import com.fclinic.patientservice.domain.model.ClinicalData;
import java.time.Instant;
import java.util.UUID;

public record MedicalRecord(UUID id, UUID patientId, UUID doctorId, UUID appointmentId, Instant visitDate,
                            String symptoms, String diagnosis, String treatment, String notes, long version,
                            Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {
    public static MedicalRecord create(UUID patientId, UUID doctorId, UUID appointmentId,
                                       ClinicalData data, String actor, Instant now) {
        return new MedicalRecord(UUID.randomUUID(), patientId, doctorId, appointmentId, data.visitDate(), data.symptoms(),
                data.diagnosis(), data.treatment(), data.notes(), 0, now, now, actor, actor);
    }
    public ClinicalData clinical() { return new ClinicalData(visitDate, symptoms, diagnosis, treatment, notes); }
    public MedicalRecord replace(ClinicalData data, String actor, Instant now) {
        return new MedicalRecord(id, patientId, doctorId, appointmentId, data.visitDate(), data.symptoms(),
                data.diagnosis(), data.treatment(), data.notes(), version, createdAt, now, createdBy, actor);
    }
}
