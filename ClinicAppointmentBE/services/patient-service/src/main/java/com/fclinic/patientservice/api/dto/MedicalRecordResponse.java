package com.fclinic.patientservice.api.dto;
import com.fclinic.patientservice.domain.aggregate.MedicalRecord;
import java.time.Instant;
import java.util.UUID;

public record MedicalRecordResponse(long id, UUID patientId, long doctorId, Long appointmentId, Instant visitDate,
                                    String symptoms, String diagnosis, String treatment, String notes, long version,
                                    Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {
    public static MedicalRecordResponse from(MedicalRecord r) {
        return new MedicalRecordResponse(r.id(), r.patientId(), r.doctorId(), r.appointmentId(), r.visitDate(),
                r.symptoms(), r.diagnosis(), r.treatment(), r.notes(), r.version(), r.createdAt(), r.updatedAt(),
                r.createdBy(), r.updatedBy());
    }
}
