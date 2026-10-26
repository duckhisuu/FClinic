package com.fclinic.patientservice.domain.aggregate;
import com.fclinic.patientservice.domain.model.ClinicalData;
import java.time.Instant;
import java.util.UUID;

public record MedicalRecord(Long id, UUID patientId, long doctorId, Long appointmentId, Instant visitDate,
                            String symptoms, String diagnosis, String treatment, String notes, long version,
                            Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {
    public static MedicalRecord create(UUID patientId, long doctorId, Long appointmentId,
                                       ClinicalData data, String actor, Instant now) {
        return new MedicalRecord(null, patientId, doctorId, appointmentId, data.visitDate(), data.symptoms(),
                data.diagnosis(), data.treatment(), data.notes(), 0, now, now, actor, actor);
    }
    public ClinicalData clinical() { return new ClinicalData(visitDate, symptoms, diagnosis, treatment, notes); }
    public MedicalRecord replace(ClinicalData data, String actor, Instant now) {
        return new MedicalRecord(id, patientId, doctorId, appointmentId, data.visitDate(), data.symptoms(),
                data.diagnosis(), data.treatment(), data.notes(), version, createdAt, now, createdBy, actor);
    }
}
