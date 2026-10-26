package com.fclinic.patientservice.infrastructure.adapter.reference;

import com.fclinic.patientservice.application.port.out.DoctorClientPort.DoctorReference;
import com.fclinic.patientservice.domain.model.PatientError;
import java.util.*;
import static com.fclinic.patientservice.infrastructure.adapter.reference.ReferenceJson.*;

public final class DoctorReferenceDecoder {
    private static final Set<String> FIELDS=Set.of("id","name","specialty","department","qualification","experienceYears",
            "consultationFee","roomNumber","bio","avatarUrl","active");
    public Optional<DoctorReference> decode(int status,byte[] bytes,long requestedId) {
        try {
            requireId(requestedId);
            var data=body(status,bytes,"DOCTOR_NOT_FOUND");
            if(data==null) return Optional.empty();
            object(data,FIELDS); long id=id(data.path("id")); check(id==requestedId);
            for(String field:List.of("name","specialty","department")) string(data.path(field),false);
            for(String field:List.of("qualification","roomNumber","bio","avatarUrl")) string(data.path(field),true);
            var years=data.path("experienceYears");
            check(years.isNull() || (years.isIntegralNumber() && years.canConvertToInt()));
            var fee=data.path("consultationFee");
            check(fee.isNull() || (fee.isNumber() && Double.isFinite(fee.doubleValue()) && fee.decimalValue().signum()>=0));
            check(data.path("active").isBoolean());
            return Optional.of(new DoctorReference(id,data.path("active").asBoolean()));
        } catch(PatientError e) { throw e; }
        catch(RuntimeException e) { throw unavailable(); }
    }
}

