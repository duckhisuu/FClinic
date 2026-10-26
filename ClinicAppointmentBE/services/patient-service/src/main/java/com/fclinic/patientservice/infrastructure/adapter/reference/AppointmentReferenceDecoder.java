package com.fclinic.patientservice.infrastructure.adapter.reference;

import com.fclinic.patientservice.application.port.out.AppointmentClientPort.AppointmentReference;
import com.fclinic.patientservice.domain.model.PatientError;
import java.util.*;
import static com.fclinic.patientservice.infrastructure.adapter.reference.ReferenceJson.*;

public final class AppointmentReferenceDecoder {
    /** Pin target draft vocabulary; no default from missing/unknown status to non-cancelled. */
    private enum Status { CONFIRMED, CANCELLED, COMPLETED, NO_SHOW }
    public Optional<AppointmentReference> decode(int status,byte[] bytes,long requestedId) {
        try {
            requireId(requestedId);
            var data=body(status,bytes,"APPOINTMENT_NOT_FOUND");
            if(data==null) return Optional.empty();
            object(data,Set.of("id","patientId","doctorId","status"));
            long id=id(data.path("id")); check(id==requestedId);
            String patient=string(data.path("patientId"),false);
            check(patient.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
            UUID patientId=UUID.fromString(patient);
            long doctorId=id(data.path("doctorId"));
            Status appointmentStatus=Status.valueOf(string(data.path("status"),false));
            return Optional.of(new AppointmentReference(id,patientId,doctorId,appointmentStatus==Status.CANCELLED));
        } catch(PatientError e) { throw e; }
        catch(RuntimeException e) { throw unavailable(); }
    }
}

