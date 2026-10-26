package com.fclinic.patientservice.identity;

import com.fclinic.patientservice.infrastructure.adapter.identity.*;
import com.fclinic.patientservice.domain.model.PatientError;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import static com.fclinic.patientservice.identity.IdentityFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static com.fclinic.patientservice.domain.model.PatientError.Code.*;

class IntrospectionTransportTest {
    TestAuthServer auth;
    @BeforeEach void start() { auth=new TestAuthServer(); }
    @AfterEach void stop() { auth.close(); }
    @Test void sendsBackendCredentialAndOpaqueTokenUsingTlsFormPost() {
        auth.handler=token -> { assertThat(token).isEqualTo("opaque+/=="); return TestAuthServer.Reply.json(200,bytes(user())); };
        try(var verifier=new IntrospectionIdentityVerifier(auth.settings(Duration.ofSeconds(3)),CLOCK)) {
            assertThat(verifier.verify("opaque+/==").patientId()).isEqualTo(PATIENT);
            assertThat(auth.authenticatedCalls.get()).isEqualTo(1);
        }
    }
    @Test void invalidInputNeverContactsProvider() {
        try(var verifier=new IntrospectionIdentityVerifier(auth.settings(Duration.ofSeconds(3)),CLOCK)) {
            error(UNAUTHENTICATED,() -> verifier.verify("bad,token")); assertThat(auth.calls.get()).isZero();
        }
    }
    @Test void providerCredentialErrorsRedirectAndServerErrorsAre503WithoutRetry() {
        try(var verifier=new IntrospectionIdentityVerifier(auth.settings(Duration.ofSeconds(3)),CLOCK)) {
            for(int status:new int[]{400,401,403,404,429,500,503,302}) {
                auth.handler=t -> TestAuthServer.Reply.json(status,bytes(Map.of("private","must not leak")));
                int before=auth.calls.get();
                error(DEPENDENCY_UNAVAILABLE,() -> verifier.verify("fixture"));
                assertThat(auth.calls.get()).isEqualTo(before+1);
            }
        }
    }
    @Test void wrongContentTypeLargeBodyAndMalformedPayloadAre503() {
        try(var verifier=new IntrospectionIdentityVerifier(auth.settings(Duration.ofSeconds(3)),CLOCK)) {
            for(var reply:new TestAuthServer.Reply[]{
                    new TestAuthServer.Reply(200,"text/html",bytes(user())),
                    TestAuthServer.Reply.json(200,"x".repeat(65537).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    TestAuthServer.Reply.json(200,"{".getBytes(java.nio.charset.StandardCharsets.UTF_8))}) {
                auth.handler=t -> reply; error(DEPENDENCY_UNAVAILABLE,() -> verifier.verify("fixture"));
            }
        }
    }
    @Test void responseDeadlineAndConcurrencyLimitFailClosed() throws Exception {
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        auth.handler=t -> {
            entered.countDown();
            try { release.await(10,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            return TestAuthServer.Reply.json(200,bytes(user()));
        };
        var s=auth.settings(Duration.ofSeconds(2));
        var one=new IdentitySettings(true,s.endpoint(),s.issuer(),s.audience(),s.clientId(),s.clientSecret(),
                s.connectTimeout(),s.exchangeTimeout(),s.maxResponseBytes(),1,s.trustStore(),s.trustStorePassword(),s.services());
        try(var verifier=new IntrospectionIdentityVerifier(one,CLOCK); var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=pool.submit(() -> { error(DEPENDENCY_UNAVAILABLE,() -> verifier.verify("fixture")); });
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            error(DEPENDENCY_UNAVAILABLE,() -> verifier.verify("fixture"));
            first.get(5,TimeUnit.SECONDS);
            assertThat(auth.calls.get()).isEqualTo(1);
        } finally { release.countDown(); }
    }
    @Test void untrustedTlsCertificateFailsClosed() {
        var s=auth.settings(Duration.ofSeconds(3));
        var systemTrust=new IdentitySettings(true,s.endpoint(),s.issuer(),s.audience(),s.clientId(),s.clientSecret(),
                s.connectTimeout(),s.exchangeTimeout(),s.maxResponseBytes(),s.maxConcurrentRequests(),null,null,s.services());
        try(var verifier=new IntrospectionIdentityVerifier(systemTrust,CLOCK)) {
            error(DEPENDENCY_UNAVAILABLE,() -> verifier.verify("fixture"));
            assertThat(auth.authenticatedCalls.get()).isZero();
        }
    }
    @Test void currentActivationIsCheckedAgainWithoutPositiveCaching() {
        try(var verifier=new IntrospectionIdentityVerifier(auth.settings(Duration.ofSeconds(3)),CLOCK)) {
            assertThat(verifier.verify("fixture").active()).isTrue();
            auth.handler=t -> TestAuthServer.Reply.json(200,bytes(Map.of("active",false)));
            error(UNAUTHENTICATED,() -> verifier.verify("fixture")); assertThat(auth.calls.get()).isEqualTo(2);
        }
    }
    @Test void trustedCertificateWithWrongHostnameIsStillRejected() {
        try(var wrongHost=new TestAuthServer(new TestAuthServer.TestTls("dns:other.invalid"));
            var verifier=new IntrospectionIdentityVerifier(wrongHost.settings(Duration.ofSeconds(3)),CLOCK)) {
            error(DEPENDENCY_UNAVAILABLE,() -> verifier.verify("fixture"));
            assertThat(wrongHost.authenticatedCalls.get()).isZero();
        }
    }
    @Test void incorrectBackendSecretIs503NotHuman401() {
        var s=auth.settings(Duration.ofSeconds(3));
        var wrong=new IdentitySettings(true,s.endpoint(),s.issuer(),s.audience(),s.clientId(),"wrong-test-secret",
                s.connectTimeout(),s.exchangeTimeout(),s.maxResponseBytes(),s.maxConcurrentRequests(),
                s.trustStore(),s.trustStorePassword(),s.services());
        try(var verifier=new IntrospectionIdentityVerifier(wrong,CLOCK)) {
            error(DEPENDENCY_UNAVAILABLE,() -> verifier.verify("fixture"));
            assertThat(auth.calls.get()).isEqualTo(1); assertThat(auth.authenticatedCalls.get()).isZero();
        }
    }
    private static void error(PatientError.Code code,Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PatientError.class,e -> {
            assertThat(e.code()).isEqualTo(code); assertThat(e.getMessage()).isEqualTo(code.name());
            assertThat(e.getCause()).isNull();
        });
    }
}

