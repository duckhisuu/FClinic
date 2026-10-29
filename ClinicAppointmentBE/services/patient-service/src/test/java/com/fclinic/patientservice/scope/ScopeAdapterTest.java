package com.fclinic.patientservice.scope;

import com.fclinic.patientservice.infrastructure.adapter.scope.*;
import com.fclinic.patientservice.application.port.out.WorkScopePort;
import com.fclinic.patientservice.domain.model.PatientError;
import com.fclinic.patientservice.identity.TestAuthServer;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static com.fclinic.patientservice.scope.ScopeFixtures.*;
import static com.fclinic.patientservice.application.port.out.WorkScopePort.*;
import static org.assertj.core.api.Assertions.*;

class ScopeAdapterTest {
    TestScopeServer server;
    RemoteWorkScopeAdapter adapter;
    @BeforeEach void start() { server=new TestScopeServer(); adapter=new RemoteWorkScopeAdapter(server.settings(Duration.ofSeconds(3)),CLOCK); }
    @AfterEach void stop() { adapter.close(); server.close(); }
    @Test void separateAuthoritiesReceiveOperationBoundCallerAndResources() {
        assertThat(adapter.receptionist(STAFF,AdministrativeAction.CREATE)).isTrue();
        assertThat(adapter.profile(STAFF,PATIENT,ProfileAction.ADMIN_UPDATE)).isTrue();
        assertThat(adapter.clinical(DOCTOR,PATIENT,ClinicalAction.READ)).isTrue();
        assertThat(server.adminCalls.get()).isEqualTo(2); assertThat(server.clinicalCalls.get()).isEqualTo(1);
        assertThat(server.requests.get(2).get("operation")).isEqualTo("MEDICAL_READ");
        assertThat(nested(server.requests.get(2),"caller").get("doctorId").toString()).isEqualTo("12");
        assertThat(adapter.receptionist(DOCTOR)).isFalse();
    }
    @Test void roleAloneDoesNotGrantAndSearchNullEmptyBoundedStayDistinct() {
        server.handler=(kind,request) -> TestAuthServer.Reply.json(200,bytes(deny(request)));
        assertThat(adapter.receptionist(STAFF)).isFalse(); assertThat(adapter.clinical(DOCTOR,PATIENT)).isFalse();
        assertThat(adapter.search(STAFF,SearchAction.ADMINISTRATIVE)).isNull();
        server.handler=(kind,request) -> TestAuthServer.Reply.json(200,bytes(grant(request,Set.of())));
        assertThat(adapter.search(STAFF,SearchAction.ADMINISTRATIVE).patientIds()).isEmpty();
        server.handler=(kind,request) -> TestAuthServer.Reply.json(200,bytes(grant(request,Set.of(PATIENT))));
        assertThat(adapter.search(DOCTOR,SearchAction.CLINICAL).patientIds()).containsExactly(PATIENT);
    }
    @Test void linkedAndWalkInBothRequireVisitAuthorityAndNoPositiveCache() {
        assertThat(adapter.validVisit(DOCTOR,PATIENT,102L,CLOCK.instant())).isTrue();
        assertThat(adapter.validVisit(DOCTOR,PATIENT,null,CLOCK.instant())).isTrue();
        assertThat(server.requests.get(0).get("appointmentId").toString()).isEqualTo("102");
        assertThat(server.requests.get(1).get("appointmentId")).isNull();
        assertThat(nested(server.requests.get(1),"caller").get("subject")).isEqualTo(DOCTOR.subject());
        server.handler=(kind,request) -> TestAuthServer.Reply.json(200,bytes(deny(request)));
        assertThat(adapter.validVisit(DOCTOR,PATIENT,null,CLOCK.instant())).isFalse();
        assertThat(server.clinicalCalls.get()).isEqualTo(3);
    }
    @Test void allProviderHttpErrorsAre503NotFalseAndNoApplicationRetry() {
        for(int status:new int[]{400,401,403,404,429,500,503,302}) {
            server.handler=(kind,request) -> TestAuthServer.Reply.json(status,bytes(Map.of("error","private-provider-detail")));
            int before=server.adminCalls.get(); unavailable(() -> adapter.receptionist(STAFF));
            assertThat(server.adminCalls.get()).isEqualTo(before+1);
        }
    }
    @Test void malformedOversizedAndWrongContentTypeAre503() {
        for(var reply:new TestAuthServer.Reply[]{
                TestAuthServer.Reply.json(200,"{}".getBytes()),
                TestAuthServer.Reply.json(200,"x".repeat(4097).getBytes()),
                new TestAuthServer.Reply(200,"text/html","{}".getBytes())}) {
            server.handler=(kind,request) -> reply; unavailable(() -> adapter.receptionist(STAFF));
        }
    }
    @Test void noScopeNetworkLookupInsideLocalTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { unavailable(() -> adapter.clinical(DOCTOR,PATIENT)); assertThat(server.clinicalCalls.get()).isZero(); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }
    @Test void credentialFailureAndUntrustedTlsAre503() {
        var s=server.authority(ScopeQuery.Authority.ADMINISTRATIVE,Duration.ofSeconds(3));
        for(boolean wrongSecret:new boolean[]{true,false}) {
            var invalid=new ScopeSettings.Authority(s.endpoint(),s.clientId(),wrongSecret?"wrong-test-secret":s.clientSecret(),
                    s.connectTimeout(),s.exchangeTimeout(),s.maxResponseBytes(),s.maxConcurrentRequests(),s.maxSearchPatients(),
                    wrongSecret?s.trustStore():null,wrongSecret?s.trustStorePassword():null);
            try(var client=new ScopeAuthorityClient(invalid)) { unavailable(() -> client.evaluate(query(ScopeQuery.Operation.ADMIN_ASSIGNMENT_CREATE))); }
        }
    }
    @Test void validTrustChainWithWrongHostnameIsRejected() {
        try(var badHost=new TestScopeServer(new TestAuthServer.TestTls("dns:other.invalid"));
            var client=new ScopeAuthorityClient(badHost.authority(ScopeQuery.Authority.ADMINISTRATIVE,Duration.ofSeconds(3)))) {
            unavailable(() -> client.evaluate(query(ScopeQuery.Operation.ADMIN_ASSIGNMENT_CREATE)));
            assertThat(badHost.requests).isEmpty();
        }
    }
    @Test void deadlineAndCapacityBoundedWithLatchNotSleep() throws Exception {
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        server.handler=(kind,request) -> {
            entered.countDown();
            try { release.await(10,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            return TestAuthServer.Reply.json(200,bytes(grant(request,Set.of())));
        };
        var s=server.authority(ScopeQuery.Authority.ADMINISTRATIVE,Duration.ofSeconds(2));
        var one=new ScopeSettings.Authority(s.endpoint(),s.clientId(),s.clientSecret(),s.connectTimeout(),s.exchangeTimeout(),
                s.maxResponseBytes(),1,s.maxSearchPatients(),s.trustStore(),s.trustStorePassword());
        try(var client=new ScopeAuthorityClient(one); var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=pool.submit(() -> unavailable(() -> client.evaluate(query(ScopeQuery.Operation.ADMIN_ASSIGNMENT_CREATE))));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            unavailable(() -> client.evaluate(query(ScopeQuery.Operation.ADMIN_ASSIGNMENT_CREATE)));
            first.get(5,TimeUnit.SECONDS); assertThat(server.adminCalls.get()).isEqualTo(1);
        } finally { release.countDown(); }
    }
    private static void unavailable(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PatientError.class,e -> {
            assertThat(e.code()).isEqualTo(PatientError.Code.DEPENDENCY_UNAVAILABLE);
            assertThat(e.getMessage()).isEqualTo("DEPENDENCY_UNAVAILABLE"); assertThat(e.getCause()).isNull();
        });
    }
}

