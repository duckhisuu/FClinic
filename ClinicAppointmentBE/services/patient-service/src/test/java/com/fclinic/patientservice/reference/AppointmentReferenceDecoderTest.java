package com.fclinic.patientservice.reference;

import com.fclinic.patientservice.infrastructure.adapter.reference.AppointmentReferenceDecoder;
import com.fclinic.patientservice.domain.model.PatientError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static com.fclinic.patientservice.reference.ReferenceFixtures.*;
import static org.assertj.core.api.Assertions.*;

class AppointmentReferenceDecoderTest {
    final AppointmentReferenceDecoder decoder=new AppointmentReferenceDecoder();
    @ParameterizedTest @ValueSource(strings={"CONFIRMED","CANCELLED","COMPLETED","NO_SHOW"})
    void fullVocabularyIsValidatedBeforeCancelledBoolean(String status) {
        var fact=decoder.decode(200,bytes(appointment(102,PATIENT,12,status)),102).orElseThrow();
        assertThat(fact.cancelled()).isEqualTo(status.equals("CANCELLED"));
        assertThat(fact.patientId()).isEqualTo(PATIENT); assertThat(fact.doctorId()).isEqualTo(12);
    }
    @ParameterizedTest @ValueSource(strings={"id","patientId","doctorId","status"})
    void requiredFieldsCannotBeMissingOrNull(String field) {
        var body=appointment(102,PATIENT,12,"CONFIRMED"); data(body).remove(field); fail(200,bytes(body),102);
        body=appointment(102,PATIENT,12,"CONFIRMED"); data(body).put(field,null); fail(200,bytes(body),102);
    }
    @Test void unknownOrWrongTypeStatusIsNeverNonCancelled() {
        for(Object status:List.of("PENDING","UNKNOWN","cancelled","",false,0)) {
            var body=appointment(102,PATIENT,12,"CONFIRMED"); data(body).put("status",status); fail(200,bytes(body),102);
        }
    }
    @Test void patientAndDoctorMismatchRemainFactsForApplication422Comparison() {
        UUID other=UUID.randomUUID();
        var fact=decoder.decode(200,bytes(appointment(102,other,13,"CONFIRMED")),102).orElseThrow();
        assertThat(fact.patientId()).isEqualTo(other); assertThat(fact.doctorId()).isEqualTo(13);
        fail(200,bytes(appointment(103,PATIENT,12,"CONFIRMED")),102);
    }
    @Test void uuidAndLongTypesRangesAndResourceIdAreStrict() {
        for(Object patient:List.of(1,"1","11111111-1111-4111-8111-11111111111A","11111111-1111-1111-1111-111111111111")) {
            var body=appointment(102,PATIENT,12,"CONFIRMED"); data(body).put("patientId",patient); fail(200,bytes(body),102);
        }
        for(String field:List.of("id","doctorId"))
            for(Object id:List.of(0,-1,9007199254740992L,12.0,"12")) {
                var body=appointment(102,PATIENT,12,"CONFIRMED"); data(body).put(field,id); fail(200,bytes(body),102);
            }
        long max=9007199254740991L;
        assertThat(decoder.decode(200,bytes(appointment(max,PATIENT,max,"COMPLETED")),max).orElseThrow().doctorId()).isEqualTo(max);
    }
    @Test void onlyExactSelected404ReturnsEmpty() {
        assertThat(decoder.decode(404,bytes(missing("APPOINTMENT_NOT_FOUND")),102)).isEmpty();
        fail(404,bytes(missing("DOCTOR_NOT_FOUND")),102);
        var legacy=missing("APPOINTMENT_NOT_FOUND"); legacy.remove("code"); fail(404,bytes(legacy),102);
        fail(401,bytes(missing("APPOINTMENT_NOT_FOUND")),102);
    }
    @Test void publicFullPayloadAndUnknownDuplicateOrMalformedJsonAre503() {
        var full=appointment(102,PATIENT,12,"CONFIRMED"); data(full).put("patientName","Synthetic extra"); fail(200,bytes(full),102);
        var wrapped=appointment(102,PATIENT,12,"CONFIRMED"); wrapped.put("version","unexpected"); fail(200,bytes(wrapped),102);
        fail(200,bytes(data(appointment(102,PATIENT,12,"CONFIRMED"))),102);
        for(String raw:List.of("null","[]","{","{} {}","{\"status\":200,\"status\":404}")) fail(200,raw.getBytes(),102);
        String duplicate=JSON.writeValueAsString(appointment(102,PATIENT,12,"CONFIRMED"))
                .replace("\"status\":\"CONFIRMED\"","\"status\":\"CONFIRMED\",\"status\":\"CANCELLED\"");
        fail(200,duplicate.getBytes(),102);
    }
    private void fail(int status,byte[] bytes,long id) {
        assertThatThrownBy(() -> decoder.decode(status,bytes,id)).isInstanceOfSatisfying(PatientError.class,
                e -> assertThat(e.code()).isEqualTo(PatientError.Code.DEPENDENCY_UNAVAILABLE));
    }
}

