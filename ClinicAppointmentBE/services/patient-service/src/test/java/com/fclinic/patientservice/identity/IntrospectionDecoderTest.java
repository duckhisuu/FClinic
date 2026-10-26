package com.fclinic.patientservice.identity;

import com.fclinic.patientservice.infrastructure.adapter.identity.*;
import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.domain.model.PatientError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static com.fclinic.patientservice.identity.IdentityFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static com.fclinic.patientservice.domain.model.PatientError.Code.*;

class IntrospectionDecoderTest {
    final IntrospectionDecoder decoder = new IntrospectionDecoder(settings(),CLOCK);
    @Test void validUserPreservesOpaqueSubjectAndMappings() {
        var body=user(); body.put("doctorId",12L); body.put("authorities",List.of("ROLE_DOCTOR","ROLE_PATIENT"));
        var c=decoder.decode(bytes(body));
        assertThat(c.subject()).isEqualTo("opaque-human-fixture");
        assertThat(c.patientId()).isEqualTo(PATIENT); assertThat(c.doctorId()).isEqualTo(12);
        assertThat(c.serviceName()).isNull(); assertThat(c.principalType()).isEqualTo(TrustedCaller.PrincipalType.USER);
    }
    @Test void validServiceNameComesOnlyFromExactRegistryBinding() {
        var c=decoder.decode(bytes(service()));
        assertThat(c.serviceName()).isEqualTo("appointment-service");
        assertThat(c.subject()).isEqualTo(SERVICE_SUBJECT); assertThat(c.patientId()).isNull();
        assertThat(c.authorities()).containsExactly("SVC_PATIENT_READ");
    }
    @Test void unknownServiceSubjectDoesNotAcquireServiceName() {
        var body=service(); body.put("sub","appointment-service");
        error(FORBIDDEN,bytes(body));
    }
    @Test void serviceCannotExceedLocalAuthorityCeiling() {
        var body=service(); body.put("authorities",List.of("SVC_PATIENT_WRITE"));
        error(FORBIDDEN,bytes(body));
    }
    @Test void inactiveInvalidOrRevokedCredentialsAre401() { error(UNAUTHENTICATED,bytes(Map.of("active",false))); }
    @Test void expiryAtClockBoundaryIs401() {
        var body=user(); body.put("exp",CLOCK.instant().getEpochSecond()); error(UNAUTHENTICATED,bytes(body));
    }
    @Test void wrongIssuerIs401() { var body=user(); body.put("iss","https://wrong.example.test"); error(UNAUTHENTICATED,bytes(body)); }
    @Test void wrongAudienceIs401() { var body=user(); body.put("aud",List.of("other-service")); error(UNAUTHENTICATED,bytes(body)); }
    @Test void audienceMayBeSingleString() {
        var body=user(); body.put("aud","patient-service"); assertThat(decoder.decode(bytes(body)).active()).isTrue();
    }
    @Test void unlinkedUserIsAuthenticatedButHasNoSelfMapping() {
        var body=user(); body.put("patientId",null);
        assertThat(decoder.decode(bytes(body)).patientId()).isNull();
    }
    @Test void doctorMappingMustBeSafePositiveInteger() {
        for (Object id:List.of(0,-1,1.5,"12",9007199254740992L)) {
            var body=user(); body.put("doctorId",id); error(DEPENDENCY_UNAVAILABLE,bytes(body));
        }
    }
    @Test void serviceCannotCarryHumanMappingsOrRoles() {
        for (String field:List.of("userId","doctorId","patientId","authorities")) {
            var body=service(); body.put(field,user().get(field)==null?12L:user().get(field));
            error(DEPENDENCY_UNAVAILABLE,bytes(body));
        }
    }
    @ParameterizedTest @ValueSource(strings={"serviceName","phone","email","scope","valid"})
    void unknownOrForgedWireFieldsAre503(String field) {
        var body=user(); body.put(field,"appointment-service"); error(DEPENDENCY_UNAVAILABLE,bytes(body));
    }
    @ParameterizedTest @ValueSource(strings={"active","contractVersion","iss","aud","exp","sub","principalType","userId","patientId","doctorId","authorities"})
    void missingRequiredFieldIs503(String field) {
        var body=user(); body.remove(field); error(DEPENDENCY_UNAVAILABLE,bytes(body));
    }
    @Test void nullRequiredDataAndUnknownRoleAre503() {
        for(String field:List.of("active","iss","aud","sub","exp","principalType","userId","authorities","contractVersion")) {
            var body=user(); body.put(field,null); error(DEPENDENCY_UNAVAILABLE,bytes(body));
        }
        var body=user(); body.put("authorities",List.of("ROLE_SUPERUSER")); error(DEPENDENCY_UNAVAILABLE,bytes(body));
    }
    @Test void malformedIdentityTypesAndAmbiguousPayloadsAre503() {
        for(String payload:List.of("null","[]","{}","{\"active\":true,\"active\":false}",
                "{\"active\":false} {}","{\"active\":\"false\"}","{\"active\":false,\"sub\":\"x\"}")) {
            error(DEPENDENCY_UNAVAILABLE,payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        var body=user(); body.put("patientId","11111111-1111-4111-8111-11111111111A"); error(DEPENDENCY_UNAVAILABLE,bytes(body));
        body=user(); body.put("authorities",List.of("ROLE_PATIENT","ROLE_PATIENT")); error(DEPENDENCY_UNAVAILABLE,bytes(body));
        body=user(); body.put("principalType","ANONYMOUS"); error(DEPENDENCY_UNAVAILABLE,bytes(body));
    }
    private void error(PatientError.Code code,byte[] body) {
        assertThatThrownBy(() -> decoder.decode(body)).isInstanceOfSatisfying(PatientError.class,
                e -> { assertThat(e.code()).isEqualTo(code); assertThat(e.getMessage()).isEqualTo(code.name()); });
    }
}

