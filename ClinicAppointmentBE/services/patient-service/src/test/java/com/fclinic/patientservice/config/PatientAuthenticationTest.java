package com.fclinic.patientservice.config;

import com.fclinic.patientservice.api.security.PatientAuthentication;
import com.fclinic.patientservice.application.security.PatientPolicy;
import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.domain.model.PatientError;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PatientAuthenticationTest {
    private final PatientConfiguration config=new PatientConfiguration();
    @Test void tc17_missingOrMalformedBearerAndSpoofedHeadersAre401() {
        var interceptor=new PatientAuthentication(config.identityVerifier(),new PatientPolicy(config.workScope()));
        for(String bearer:List.of("","Basic abc","Bearer ","Bearer abc def")) {
            var request=new MockHttpServletRequest();
            if(!bearer.isEmpty()) request.addHeader("Authorization",bearer);
            request.addHeader("X-Role","ROLE_ADMIN"); request.addHeader("X-User-Id","401");
            assertError(PatientError.Code.UNAUTHENTICATED,() -> interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
        }
    }
    @Test void unconfiguredVerifierNeverAcceptsAnArbitraryBearerAndReturns503() {
        var interceptor=new PatientAuthentication(config.identityVerifier(),new PatientPolicy(config.workScope()));
        var request=new MockHttpServletRequest(); request.addHeader("Authorization","Bearer arbitrary-test-input");
        assertError(PatientError.Code.DEPENDENCY_UNAVAILABLE,() -> interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
        assertThat(request.getAttribute("patientCaller")).isNull();
    }
    @Test void inactiveVerifiedContextIs401() {
        var context=new TrustedCaller("user:demo",TrustedCaller.PrincipalType.USER,Set.of("ROLE_PATIENT"),UUID.randomUUID(),null,null,false);
        var interceptor=new PatientAuthentication(token -> context,new PatientPolicy(config.workScope()));
        var request=new MockHttpServletRequest(); request.addHeader("Authorization","Bearer component-fixture");
        assertError(PatientError.Code.UNAUTHENTICATED,() -> interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
    }
    @Test void defaultScopeAndReferenceAdaptersFailClosed() {
        var c=new TrustedCaller("user:demo",TrustedCaller.PrincipalType.USER,Set.of("ROLE_DOCTOR"),null,12L,null,true);
        assertThat(config.workScope().clinical(c,UUID.randomUUID())).isFalse();
        assertThat(config.workScope().search(c)).isNull();
        assertError(PatientError.Code.DEPENDENCY_UNAVAILABLE,() -> config.doctorClient().find(12));
        assertError(PatientError.Code.DEPENDENCY_UNAVAILABLE,() -> config.appointmentClient().find(102));
    }
    private void assertError(PatientError.Code code,Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(PatientError.class,e -> assertThat(e.code()).isEqualTo(code));
    }
}

