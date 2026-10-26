package com.fclinic.patientservice.scope;

import com.fclinic.patientservice.infrastructure.adapter.scope.*;
import com.fclinic.patientservice.domain.model.PatientError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static com.fclinic.patientservice.scope.ScopeFixtures.*;
import static org.assertj.core.api.Assertions.*;

class ScopeDecisionDecoderTest {
    final ScopeDecisionDecoder decoder=new ScopeDecisionDecoder(CLOCK);
    @ParameterizedTest @ValueSource(strings={"ADMIN_ASSIGNMENT_CREATE","ADMIN_PROFILE_READ","ADMIN_PROFILE_UPDATE",
            "DOCTOR_PROFILE_READ","MEDICAL_READ","MEDICAL_CREATE","MEDICAL_UPDATE","APPOINTMENT_PROFILE_READ","VALID_VISIT"})
    void validOperationBoundGrants(String operation) {
        var q=query(ScopeQuery.Operation.valueOf(operation));
        assertThat(decoder.decode(bytes(grant(q.wire(),Set.of())),q,100).allowed()).isTrue();
    }
    @Test void denyEmptyAndBoundedSearchAreDifferent() {
        var q=query(ScopeQuery.Operation.ADMIN_SEARCH);
        assertThat(decoder.decode(bytes(deny(q.wire())),q,100).allowed()).isFalse();
        var empty=decoder.decode(bytes(grant(q.wire(),Set.of())),q,100);
        assertThat(empty.allowed()).isTrue(); assertThat(empty.patientIds()).isEmpty();
        assertThat(decoder.decode(bytes(grant(q.wire(),Set.of(PATIENT))),q,100).patientIds()).containsExactly(PATIENT);
    }
    @ParameterizedTest @ValueSource(strings={"nonce","authority","operation","patientId","appointmentId","visitDate","caller","requestedAt"})
    void replyMustMatchExactRequestIncludingPrincipalAndResource(String field) {
        var q=query(ScopeQuery.Operation.VALID_VISIT);
        var wire=q.wire(); wire.put(field,"forged-fixture");
        fail(bytes(grant(wire,Set.of())),q);
    }
    @Test void doctorOrSubjectBindingCannotBeSubstituted() {
        for(String field:List.of("subject","doctorId","principalType","patientId","serviceName")) {
            var q=query(ScopeQuery.Operation.MEDICAL_CREATE); var wire=q.wire();
            nested(wire,"caller").put(field,"forged-fixture"); fail(bytes(grant(wire,Set.of())),q);
        }
    }
    @ParameterizedTest @ValueSource(strings={"active","future","expired"})
    void revokedFutureOrExpiredAssignmentsAreBusinessDenial(String kind) {
        var q=query(ScopeQuery.Operation.MEDICAL_CREATE); var reply=grant(q.wire(),Set.of());
        var evidence=nested(reply,"evidence");
        switch(kind) {
            case "active" -> evidence.put("active",false);
            case "future" -> evidence.put("validFrom",CLOCK.instant().plusSeconds(60).toString());
            case "expired" -> { evidence.put("validFrom",CLOCK.instant().minusSeconds(100).toString()); evidence.put("validUntil",CLOCK.instant().toString()); }
        }
        assertThat(decoder.decode(bytes(reply),q,100).allowed()).isFalse();
    }
    @Test void walkInStillRequiresAuthoritativeVisitEvidence() {
        var q=ScopeQuery.create(ScopeQuery.Authority.CLINICAL,ScopeQuery.Operation.VALID_VISIT,DOCTOR,PATIENT,null,
                CLOCK.instant().minusSeconds(60),CLOCK.instant());
        assertThat(decoder.decode(bytes(grant(q.wire(),Set.of())),q,100).allowed()).isTrue();
        var reply=grant(q.wire(),Set.of()); nested(reply,"evidence").put("visitId",null); fail(bytes(reply),q);
    }
    @Test void missingUnknownNullOrWrongTypesFail503() {
        var q=query(ScopeQuery.Operation.MEDICAL_READ);
        for(String field:List.of("contractVersion","request","evaluatedAt","allowed","patientIds","evidence")) {
            var reply=grant(q.wire(),Set.of()); reply.remove(field); fail(bytes(reply),q);
        }
        var reply=grant(q.wire(),Set.of()); reply.put("scope","ALL"); fail(bytes(reply),q);
        reply=grant(q.wire(),Set.of()); reply.put("allowed","true"); fail(bytes(reply),q);
        reply=grant(q.wire(),Set.of()); nested(reply,"evidence").put("active",null); fail(bytes(reply),q);
        reply=grant(q.wire(),Set.of()); nested(reply,"evidence").put("validUntil","2026-10-20T04:00:00+00:00"); fail(bytes(reply),q);
    }
    @Test void invalidOrUnboundedSearchDoesNotTruncateOrGrantAll() {
        var q=query(ScopeQuery.Operation.DOCTOR_SEARCH);
        for(Object ids:new Object[]{null,List.of("*"),List.of(PATIENT.toString(),PATIENT.toString()),List.of(1)}) {
            var reply=grant(q.wire(),Set.of()); reply.put("patientIds",ids); fail(bytes(reply),q);
        }
        var reply=grant(q.wire(),Set.of(PATIENT,UUID.randomUUID()));
        assertThatThrownBy(() -> decoder.decode(bytes(reply),q,1)).isInstanceOf(PatientError.class);
    }
    @Test void staleEvaluationAndReplayAre503() {
        var q=query(ScopeQuery.Operation.MEDICAL_READ);
        var reply=grant(q.wire(),Set.of()); reply.put("evaluatedAt",CLOCK.instant().minusSeconds(2).toString()); fail(bytes(reply),q);
        reply=grant(q.wire(),Set.of()); reply.put("evaluatedAt",CLOCK.instant().plusSeconds(2).toString()); fail(bytes(reply),q);
        fail(bytes(grant(q.wire(),Set.of())),query(ScopeQuery.Operation.MEDICAL_READ));
    }
    @Test void ambiguousJsonAndDenialPayloadCannotSmuggleGrant() {
        var q=query(ScopeQuery.Operation.MEDICAL_READ);
        for(String raw:List.of("null","[]","{\"allowed\":false,\"allowed\":true}","{} {}")) fail(raw.getBytes(),q);
        var reply=deny(q.wire()); reply.put("evidence",grant(q.wire(),Set.of()).get("evidence")); fail(bytes(reply),q);
    }
    private void fail(byte[] body,ScopeQuery q) {
        assertThatThrownBy(() -> decoder.decode(body,q,100)).isInstanceOfSatisfying(PatientError.class,
                e -> assertThat(e.code()).isEqualTo(PatientError.Code.DEPENDENCY_UNAVAILABLE));
    }
}

