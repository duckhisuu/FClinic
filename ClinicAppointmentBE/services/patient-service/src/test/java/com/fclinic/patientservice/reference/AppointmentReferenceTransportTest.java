package com.fclinic.patientservice.reference;
import static com.fclinic.patientservice.UuidFixtures.*;

import com.fclinic.patientservice.infrastructure.adapter.reference.*;
import com.fclinic.patientservice.domain.model.PatientError;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import static com.fclinic.patientservice.reference.ReferenceFixtures.*;
import static org.assertj.core.api.Assertions.*;

class AppointmentReferenceTransportTest {
    TestReferenceServer server;
    RemoteAppointmentClientAdapter adapter;
    @BeforeEach void start() { server=new TestReferenceServer(ReferenceHttpClient.Profile.APPOINTMENT);
        adapter=new RemoteAppointmentClientAdapter(server.settings(Duration.ofSeconds(3))); }
    @AfterEach void stop() { adapter.close(); server.close(); }
    @Test void authenticatedMinimumProjectionUsesExactReadOnlyPath() {
        assertThat(adapter.find(fixture(102)).orElseThrow().patientId()).isEqualTo(PATIENT);
        assertThat(server.requests).singleElement().satisfies(r -> {
            assertThat(r.rawPath()).isEqualTo("/internal/v1/appointments/550e8400-e29b-41d4-a716-000000000066/medical-reference");
            assertThat(r.method()).isEqualTo("GET"); assertThat(r.originalBearerForwarded()).isFalse();
        });
        assertThat(server.mutations.get()).isZero();
    }
    @Test void missingAndCancelledRemainSeparateFromTechnicalFailureWithoutCaching() {
        server.handler=id -> server.json(404,missing("APPOINTMENT_NOT_FOUND")); assertThat(adapter.find(fixture(102))).isEmpty();
        server.handler=id -> server.json(200,appointment(id,PATIENT,fixture(12),"CANCELLED"));
        assertThat(adapter.find(fixture(102)).orElseThrow().cancelled()).isTrue();
        server.handler=id -> server.json(200,appointment(id,PATIENT,fixture(12),"CONFIRMED"));
        assertThat(adapter.find(fixture(102)).orElseThrow().cancelled()).isFalse();
        server.handler=id -> server.json(200,appointment(id,PATIENT,fixture(12),"UNKNOWN")); unavailable();
        assertThat(server.calls.get()).isEqualTo(4);
    }
    @Test void errorsAndWrongVersionNeverBecomeMissingOrHumanAuthFailure() {
        for(int status:new int[]{401,403,429,500,503}) {
            server.handler=id -> server.json(status,missing("APPOINTMENT_NOT_FOUND")); unavailable();
        }
        server.handler=id -> new TestReferenceServer.Reply(200,"application/json","wrong/1",
                bytes(appointment(id,PATIENT,fixture(12),"CONFIRMED")),null); unavailable();
    }
    @Test void transactionGuardRejectsAppointmentLookupBeforeNetwork() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { unavailable(); assertThat(server.calls.get()).isZero(); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }
    private void unavailable() {
        assertThatThrownBy(() -> adapter.find(fixture(102))).isInstanceOfSatisfying(PatientError.class,e -> {
            assertThat(e.code()).isEqualTo(PatientError.Code.DEPENDENCY_UNAVAILABLE); assertThat(e.getCause()).isNull();
        });
    }
}

