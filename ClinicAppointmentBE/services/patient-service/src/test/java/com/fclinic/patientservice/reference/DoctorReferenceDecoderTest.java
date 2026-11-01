package com.fclinic.patientservice.reference;
import static com.fclinic.patientservice.UuidFixtures.*;

import com.fclinic.patientservice.infrastructure.adapter.reference.DoctorReferenceDecoder;
import com.fclinic.patientservice.domain.model.PatientError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static com.fclinic.patientservice.reference.ReferenceFixtures.*;
import static org.assertj.core.api.Assertions.*;

class DoctorReferenceDecoderTest {
    final DoctorReferenceDecoder decoder=new DoctorReferenceDecoder();
    @Test void activeInactiveAndLegitimateMissingAreDistinct() {
        assertThat(decoder.decode(200,bytes(doctor(fixture(12),true)),fixture(12)).orElseThrow().active()).isTrue();
        assertThat(decoder.decode(200,bytes(doctor(fixture(12),false)),fixture(12)).orElseThrow().active()).isFalse();
        assertThat(decoder.decode(404,bytes(missing("DOCTOR_NOT_FOUND")),fixture(12))).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"id","name","specialty","department","qualification","experienceYears","consultationFee","roomNumber","bio","avatarUrl","active"})
    void everyFullDoctorFieldMustBePresent(String field) {
        var body=doctor(fixture(12),true); data(body).remove(field); fail(200,bytes(body),fixture(12));
    }
    @Test void requiredNullOrWrongFieldTypesNeverDefaultActiveOrId() {
        for(String field:List.of("id","name","specialty","department","active")) {
            var body=doctor(fixture(12),true); data(body).put(field,null); fail(200,bytes(body),fixture(12));
        }
        for(Object active:List.of(0,"true",List.of(true))) {
            var body=doctor(fixture(12),true); data(body).put("active",active); fail(200,bytes(body),fixture(12));
        }
        var body=doctor(fixture(12),true); data(body).put("experienceYears",1.5); fail(200,bytes(body),fixture(12));
        body=doctor(fixture(12),true); data(body).put("consultationFee",-1); fail(200,bytes(body),fixture(12));
        body=doctor(fixture(12),true); data(body).put("qualification",12); fail(200,bytes(body),fixture(12));
    }
    @Test void safeIntegerBoundsAndReturnedResourceIdentityAreStrict() {
        UUID max=UUID.randomUUID();
        assertThat(decoder.decode(200,bytes(doctor(max,true)),max).orElseThrow().id()).isEqualTo(max);
        for(Object id:List.of(0,-1,9007199254740992L,12.0,"12")) {
            var body=doctor(fixture(12),true); data(body).put("id",id); fail(200,bytes(body),fixture(12));
        }
        fail(200,bytes(doctor(fixture(13),true)),fixture(12)); fail(200,bytes(doctor(fixture(12),true)),null);
    }
    @Test void onlySelected404IsBusinessMissing() {
        fail(404,bytes(missing("APPOINTMENT_NOT_FOUND")),fixture(12));
        var body=missing("DOCTOR_NOT_FOUND"); body.remove("code"); fail(404,bytes(body),fixture(12));
        body=missing("DOCTOR_NOT_FOUND"); body.put("data",data(doctor(fixture(12),true))); fail(404,bytes(body),fixture(12));
        fail(500,bytes(missing("DOCTOR_NOT_FOUND")),fixture(12));
    }
    @Test void wrongWrapperUnknownDuplicateTruncatedAndTrailingJsonAre503() {
        var body=doctor(fixture(12),true); body.put("contractVersion","unexpected"); fail(200,bytes(body),fixture(12));
        body=doctor(fixture(12),true); data(body).put("unexpected",true); fail(200,bytes(body),fixture(12));
        body=doctor(fixture(12),true); body.put("status",201); fail(200,bytes(body),fixture(12));
        for(String raw:List.of("null","[]","{","{\"status\":200,\"status\":404}","{} {}"))
            fail(200,raw.getBytes(),fixture(12));
        fail(200,bytes(data(doctor(fixture(12),true))),fixture(12));
        String raw=JSON.writeValueAsString(doctor(fixture(12),true)).replace("\"active\":true","\"active\":true,\"active\":false");
        fail(200,raw.getBytes(),fixture(12));
    }
    private void fail(int status,byte[] body,UUID id) {
        assertThatThrownBy(() -> decoder.decode(status,body,id)).isInstanceOfSatisfying(PatientError.class,e ->
                assertThat(e.code()).isEqualTo(PatientError.Code.DEPENDENCY_UNAVAILABLE));
    }
}

