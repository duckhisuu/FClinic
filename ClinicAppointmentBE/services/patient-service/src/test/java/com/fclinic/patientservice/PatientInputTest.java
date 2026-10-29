package com.fclinic.patientservice;

import com.fclinic.patientservice.api.dto.PatientInput;
import com.fclinic.patientservice.domain.model.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PatientInputTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T03:00:00Z"), ZoneOffset.UTC);
    private static final jakarta.validation.ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private final PatientInput input = new PatientInput(CLOCK, VALIDATORS.getValidator());
    @AfterAll static void closeValidation() { VALIDATORS.close(); }
    private Map<String,Object> body() { return new HashMap<>(Map.of("fullName"," Demo Patient ","phoneNumber","0900000001")); }
    @Test void tc02_normalizesSharedPhoneWithoutIdentityDeduplication() {
        var a = input.profile(body(),false);
        var b = body(); b.put("phoneNumber","+84 (900)-000-001"); b.put("email","  "); b.put("address","  ");
        assertThat(input.profile(b,false)).isEqualTo(a);
        assertThat(a.phoneNumber()).isEqualTo("+84900000001");
        assertThat(a.gender()).isEqualTo(ProfileData.Gender.UNKNOWN);
        assertThat(a.email()).isNull();
    }
    @Test void tc06_fullPutOmittedOptionalsAreNull() {
        var b=body(); b.putAll(Map.of("gender","UNKNOWN","status","INACTIVE","version",0));
        var p=input.profile(b,true);
        assertThat(p.email()).isNull(); assertThat(p.address()).isNull(); assertThat(p.dateOfBirth()).isNull();
        assertThat(p.status()).isEqualTo(ProfileData.Status.INACTIVE);
        b.remove("version"); assertThatThrownBy(() -> input.profile(b,true)).isInstanceOf(PatientError.class);
    }
    @ParameterizedTest @ValueSource(strings={""," ","2026-10-21","2025-02-29","1995-05-15T00:00:00Z"})
    void tc03_tc32_rejectsInvalidOrFutureDob(String dob) {
        var b=body(); b.put("dateOfBirth",dob);
        assertThatThrownBy(() -> input.profile(b,false)).isInstanceOf(PatientError.class);
    }
    @Test void tc32_explicitNullGenderIsDifferentFromOmitted() {
        var b=body(); b.put("gender",null);
        assertThatThrownBy(() -> input.profile(b,false)).isInstanceOf(PatientError.class);
        b.remove("gender"); b.put("dateOfBirth",null);
        assertThat(input.profile(b,false).dateOfBirth()).isNull();
    }
    @Test void tc03_unicodeWhitespaceIsBlankAndMaxLengthCountsCodePoints() {
        var blank=body(); blank.put("fullName","\u2003");
        assertThatThrownBy(() -> input.profile(blank,false)).isInstanceOf(PatientError.class);
        var unicode=body(); unicode.put("fullName","😀".repeat(255));
        assertThat(input.profile(unicode,false).fullName().codePointCount(0,510)).isEqualTo(255);
        unicode.put("fullName","😀".repeat(256));
        assertThatThrownBy(() -> input.profile(unicode,false)).isInstanceOf(PatientError.class);
    }
    @ParameterizedTest @ValueSource(strings={"id","patientCode","createdBy","status","version","unknown"})
    void tc03_rejectsUnknownAndServerOwnedFields(String field) {
        var b=body(); b.put(field,"spoof");
        assertThatThrownBy(() -> input.profile(b,false)).isInstanceOf(PatientError.class);
    }
    @ParameterizedTest @ValueSource(strings={"","abc","090000000","+84900000001x","11111111111111111111111111111111111111111"})
    void tc03_rejectsInvalidPhone(String value) {
        assertThatThrownBy(() -> input.phone(value)).isInstanceOf(PatientError.class);
    }
    @Test void tc03_rejectsNumericTextAndMalformedEmail() {
        var b=body(); b.put("fullName",123);
        assertThatThrownBy(() -> input.profile(b,false)).isInstanceOf(PatientError.class);
        var invalid=body(); invalid.put("email","bad");
        assertThatThrownBy(() -> input.profile(invalid,false)).isInstanceOf(PatientError.class);
    }
    @Test void tc05_queryAndUuidRules() {
        var q=input.query(Map.of("keyword"," Demo ","phoneNumber","0900000001"),false);
        assertThat(q.keyword()).isEqualTo("Demo"); assertThat(q.phoneNumber()).isEqualTo("+84900000001");
        assertThat(q.page()).isZero(); assertThat(q.size()).isEqualTo(10); assertThat(q.sortField()).isEqualTo("fullName");
        for (var params : List.of(Map.of("size","101"),Map.of("page","-1"),Map.of("sort","id,asc"),Map.of("phoneNumber","")))
            assertThatThrownBy(() -> input.query(params,false)).isInstanceOf(PatientError.class);
        assertThatThrownBy(() -> input.uuid("1","id")).isInstanceOf(PatientError.class);
        UUID id=UUID.randomUUID(); assertThat(input.uuid(id.toString().toUpperCase(),"id")).isEqualTo(id);
    }
    @Test void tc13_tc32_instantRequiresOffsetAndCannotBeFuture() {
        var body=new HashMap<String,Object>(Map.of("doctorId",12,"symptoms"," simulated ","diagnosis"," simulated ",
                "visitDate","2026-10-19T10:00:00+07:00"));
        assertThat(input.clinical(body,false).visitDate()).isEqualTo(Instant.parse("2026-10-19T03:00:00Z"));
        body.put("visitDate","2026-10-20T03:00:01Z");
        assertThatThrownBy(() -> input.clinical(body,false)).isInstanceOf(PatientError.class);
        body.put("visitDate","2026-10-19T03:00:00");
        assertThatThrownBy(() -> input.clinical(body,false)).isInstanceOf(PatientError.class);
    }
    @Test void tc16_tc36_medicalPutRejectsRefsAndRequiresReasonEvenForNoOp() {
        var body=new HashMap<String,Object>(Map.of("symptoms","S","diagnosis","D","visitDate","2026-10-19T03:00:00Z",
                "version",0,"changeReason"," simulated correction "));
        assertThat(input.reason(body)).isEqualTo("simulated correction");
        assertThat(input.clinical(body,true).notes()).isNull();
        body.put("doctorId",12);
        assertThatThrownBy(() -> input.clinical(body,true)).isInstanceOf(PatientError.class);
        body.remove("doctorId"); body.put("changeReason"," ");
        assertThatThrownBy(() -> input.clinical(body,true)).isInstanceOf(PatientError.class);
    }
}
