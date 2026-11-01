package com.fclinic.patientservice.reference;
import static com.fclinic.patientservice.UuidFixtures.*;

import com.fclinic.patientservice.infrastructure.adapter.reference.*;
import com.fclinic.patientservice.identity.TestAuthServer;
import com.fclinic.patientservice.domain.model.PatientError;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static com.fclinic.patientservice.reference.ReferenceFixtures.*;
import static org.assertj.core.api.Assertions.*;

class DoctorReferenceTransportTest {
    TestReferenceServer server;
    RemoteDoctorClientAdapter doctor;
    @BeforeEach void start() {
        server=new TestReferenceServer(ReferenceHttpClient.Profile.DOCTOR);
        doctor=new RemoteDoctorClientAdapter(server.settings(Duration.ofSeconds(3)));
    }
    @AfterEach void stop() { doctor.close(); server.close(); }
    @Test void authenticatedGetUsesOnlySafeDecimalIdAndExactPath() {
        UUID id=UUID.randomUUID();
        assertThat(doctor.find(id).orElseThrow().id()).isEqualTo(id);
        assertThat(server.requests).singleElement().satisfies(r -> {
            assertThat(r.rawPath()).isEqualTo("/api/v1/doctors/"+id);
            assertThat(r.method()).isEqualTo("GET"); assertThat(r.originalBearerForwarded()).isFalse();
        });
        for(UUID invalid:new UUID[]{null,UUID.fromString("550e8400-e29b-11d4-a716-446655440000"),UUID.fromString("550e8400-e29b-41d4-7716-446655440000")}) unavailable(() -> doctor.find(invalid));
        assertThat(server.calls.get()).isEqualTo(1); assertThat(server.mutations.get()).isZero();
    }
    @Test void missingRequiresVersionedDomain404AndNoPositiveCache() {
        assertThat(doctor.find(fixture(12)).orElseThrow().active()).isTrue();
        server.handler=id -> server.json(200,doctor(id,false));
        assertThat(doctor.find(fixture(12)).orElseThrow().active()).isFalse();
        server.handler=id -> server.json(404,missing("DOCTOR_NOT_FOUND")); assertThat(doctor.find(fixture(12))).isEmpty();
        server.handler=id -> server.json(404,Map.of("status",404,"message","Legacy missing")); unavailable(() -> doctor.find(fixture(12)));
        assertThat(server.calls.get()).isEqualTo(4);
    }
    @Test void providerErrorsAndRealRedirectAre503WithoutRetryOrFollowingLocation() {
        for(int status:new int[]{400,401,403,429,500,503,302}) {
            server.handler=id -> new TestReferenceServer.Reply(status,"application/json",server.profile.version,
                    bytes(Map.of("error","private-provider-detail")),server.collectionUri()+"/999");
            int before=server.calls.get(); unavailable(() -> doctor.find(fixture(12))); assertThat(server.calls.get()).isEqualTo(before+1);
        }
    }
    @Test void missingWrongVersionContentTypeOversizeAndInvalidJsonAre503() {
        for(var reply:new TestReferenceServer.Reply[]{
                new TestReferenceServer.Reply(200,"application/json",null,bytes(doctor(fixture(12),true)),null),
                new TestReferenceServer.Reply(200,"application/json","wrong/1",bytes(doctor(fixture(12),true)),null),
                new TestReferenceServer.Reply(200,"text/html",server.profile.version,bytes(doctor(fixture(12),true)),null),
                new TestReferenceServer.Reply(200,"application/json",server.profile.version,"x".repeat(4097).getBytes(),null),
                new TestReferenceServer.Reply(200,"application/json",server.profile.version,"{".getBytes(),null)}) {
            server.handler=id -> reply; unavailable(() -> doctor.find(fixture(12)));
        }
    }
    @Test void lookupRejectsLocalTransactionBeforeAnyNetworkCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { unavailable(() -> doctor.find(fixture(12))); assertThat(server.calls.get()).isZero(); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }
    @Test void backendCredentialRejectionIs503NotHuman401() {
        server.rejectAuthentication=true; unavailable(() -> doctor.find(fixture(12))); assertThat(server.requests).isEmpty();
    }
    @Test void untrustedTlsAndWrongHostnameAre503() {
        var s=server.settings(Duration.ofSeconds(3));
        var untrusted=new ReferenceSettings.Endpoint(true,s.collectionUri(),s.clientId(),s.clientSecret(),s.connectTimeout(),
                s.exchangeTimeout(),s.maxResponseBytes(),s.maxConcurrentRequests(),null,null);
        try(var invalid=new RemoteDoctorClientAdapter(untrusted)) { unavailable(() -> invalid.find(fixture(12))); }
        try(var wrongHost=new TestReferenceServer(ReferenceHttpClient.Profile.DOCTOR,new TestAuthServer.TestTls("dns:other.invalid"));
            var invalid=new RemoteDoctorClientAdapter(wrongHost.settings(Duration.ofSeconds(3)))) {
            unavailable(() -> invalid.find(fixture(12))); assertThat(wrongHost.requests).isEmpty();
        }
    }
    @Test void deadlineAndCapacityUseLatchAndNeverReturnFalseReference() throws Exception {
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        server.handler=id -> {
            entered.countDown();
            try { release.await(10,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            return server.json(200,doctor(id,true));
        };
        var s=server.settings(Duration.ofSeconds(2));
        var one=new ReferenceSettings.Endpoint(true,s.collectionUri(),s.clientId(),s.clientSecret(),s.connectTimeout(),
                s.exchangeTimeout(),s.maxResponseBytes(),1,s.trustStore(),s.trustStorePassword());
        try(var client=new RemoteDoctorClientAdapter(one); var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=pool.submit(() -> unavailable(() -> client.find(fixture(12))));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue(); unavailable(() -> client.find(fixture(12)));
            first.get(5,TimeUnit.SECONDS); assertThat(server.calls.get()).isEqualTo(1);
        } finally { release.countDown(); }
    }
    private static void unavailable(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PatientError.class,e -> {
            assertThat(e.code()).isEqualTo(PatientError.Code.DEPENDENCY_UNAVAILABLE);
            assertThat(e.getMessage()).isEqualTo("DEPENDENCY_UNAVAILABLE"); assertThat(e.getCause()).isNull();
        });
    }
}

