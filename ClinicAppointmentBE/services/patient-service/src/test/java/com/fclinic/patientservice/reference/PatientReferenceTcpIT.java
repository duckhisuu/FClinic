package com.fclinic.patientservice.reference;

import com.fclinic.patientservice.application.port.out.*;
import com.fclinic.patientservice.infrastructure.adapter.reference.*;
import com.fclinic.patientservice.infrastructure.adapter.identity.IntrospectionIdentityVerifier;
import com.fclinic.patientservice.infrastructure.adapter.scope.*;
import com.fclinic.patientservice.infrastructure.adapter.JpaRevisionAdapter;
import com.fclinic.patientservice.identity.*;
import com.fclinic.patientservice.scope.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import tools.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.fclinic.patientservice.reference.ReferenceFixtures.*;
import static com.fclinic.patientservice.scope.ScopeFixtures.CLOCK;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual Patient TCP, all FOUR production ports, five independent upstream HTTPS doubles, fresh PostgreSQL16.10. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"eureka.client.enabled=false","spring.cloud.discovery.enabled=false","server.address=127.0.0.1"})
@Import(PatientReferenceTcpIT.FixedClock.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PatientReferenceTcpIT {
    static final TestAuthServer AUTH=new TestAuthServer();
    static final TestScopeServer ADMIN=new TestScopeServer(), CLINICAL=new TestScopeServer();
    static final TestReferenceServer DOC=new TestReferenceServer(ReferenceHttpClient.Profile.DOCTOR);
    static final TestReferenceServer APT=new TestReferenceServer(ReferenceHttpClient.Profile.APPOINTMENT);
    static final EmbeddedPostgres PG=startPostgres();
    static final HttpClient HTTP=HttpClient.newHttpClient();
    static final List<String> EVENTS=new CopyOnWriteArrayList<>();
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",() -> PG.getJdbcUrl("postgres","postgres"));
        r.add("spring.datasource.username",() -> "postgres"); r.add("spring.datasource.password",() -> "postgres");
        r.add("patient.identity.enabled",() -> true); r.add("patient.identity.endpoint",AUTH::endpoint);
        r.add("patient.identity.issuer",() -> "https://identity.example.test");
        r.add("patient.identity.client-id",() -> IdentityFixtures.CLIENT_ID);
        r.add("patient.identity.client-secret",() -> AUTH.clientSecret);
        r.add("patient.identity.trust-store",() -> TestAuthServer.TLS.store);
        r.add("patient.identity.trust-store-password",() -> TestAuthServer.TLS.password);
        for(var kind:ScopeQuery.Authority.values()) {
            boolean admin=kind==ScopeQuery.Authority.ADMINISTRATIVE;
            TestScopeServer provider=admin?ADMIN:CLINICAL;
            String prefix="patient.scope."+(admin?"administrative":"clinical")+".";
            r.add("patient.scope.enabled",() -> true); r.add(prefix+"endpoint",() -> provider.endpoint(kind));
            r.add(prefix+"client-id",() -> admin?TestScopeServer.ADMIN_ID:TestScopeServer.CLINICAL_ID);
            r.add(prefix+"client-secret",() -> admin?provider.adminSecret:provider.clinicalSecret);
            r.add(prefix+"trust-store",() -> TestAuthServer.TLS.store);
            r.add(prefix+"trust-store-password",() -> TestAuthServer.TLS.password);
        }
        for(var provider:List.of(DOC,APT)) {
            String prefix="patient.reference."+(provider==DOC?"doctor":"appointment")+".";
            r.add(prefix+"enabled",() -> true); r.add(prefix+"collection-uri",provider::collectionUri);
            r.add(prefix+"client-id",() -> provider.clientId); r.add(prefix+"client-secret",() -> provider.secret);
            r.add(prefix+"trust-store",() -> TestAuthServer.TLS.store);
            r.add(prefix+"trust-store-password",() -> TestAuthServer.TLS.password);
        }
    }
    @TestConfiguration static class FixedClock { @Bean @Primary Clock referenceClock() { return CLOCK; } }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityVerifierPort identities;
    @Autowired WorkScopePort scopes;
    @Autowired DoctorClientPort doctors;
    @Autowired AppointmentClientPort appointments;
    @MockitoSpyBean JpaRevisionAdapter revisions;
    JpaRevisionAdapter revisionSpy;
    UUID own;
    @BeforeEach void setup() {
        assertThat(identities).isInstanceOf(IntrospectionIdentityVerifier.class);
        assertThat(scopes).isInstanceOf(RemoteWorkScopeAdapter.class);
        assertThat(doctors).isInstanceOf(RemoteDoctorClientAdapter.class);
        assertThat(appointments).isInstanceOf(RemoteAppointmentClientAdapter.class);
        revisionSpy=AopTestUtils.getUltimateTargetObject(revisions); reset(revisionSpy);
        jdbc.execute("TRUNCATE medical_record_revisions,patient_revisions,medical_records,patients RESTART IDENTITY CASCADE");
        AUTH.handler=this::identity;
        ADMIN.handler=(kind,q) -> { EVENTS.add("ADMIN:"+q.get("operation")); return TestAuthServer.Reply.json(200,
                ScopeFixtures.bytes(ScopeFixtures.grant(q,Set.of()))); };
        CLINICAL.handler=(kind,q) -> { EVENTS.add("CLINICAL:"+q.get("operation")); return TestAuthServer.Reply.json(200,
                ScopeFixtures.bytes(ScopeFixtures.grant(q,Set.of()))); };
        DOC.rejectAuthentication=false; APT.rejectAuthentication=false;
        DOC.handler=id -> { EVENTS.add("DOCTOR"); return DOC.json(200,doctor(id,true)); };
        APT.handler=id -> { EVENTS.add("APPOINTMENT"); return APT.json(200,appointment(id,own,12,"CONFIRMED")); };
        var profile=request("POST","/api/v2/patients","staff",profile());
        assertThat(profile.statusCode()).as(profile.body()).isEqualTo(201);
        own=UUID.fromString(json(profile).at("/data/id").asText());
        EVENTS.clear(); DOC.requests.clear(); APT.requests.clear(); DOC.calls.set(0); APT.calls.set(0);
    }
    @AfterEach void noRemoteMutations() { assertThat(DOC.mutations.get()).isZero(); assertThat(APT.mutations.get()).isZero(); }
    @AfterAll static void stop() throws Exception {
        HTTP.close(); AUTH.close(); ADMIN.close(); CLINICAL.close(); DOC.close(); APT.close(); PG.close();
    }

    @ParameterizedTest @ValueSource(strings={"CONFIRMED","COMPLETED","NO_SHOW"})
    void linkedCreateAcceptsEachSelectedNonCancelledStatusWithoutCompletionMutation(String status) {
        APT.handler=id -> { EVENTS.add("APPOINTMENT"); return APT.json(200,appointment(id,own,12,status)); };
        var created=request("POST",records(),"doctor",medical(102L));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(json(created).at("/data/patientId").asText()).isEqualTo(own.toString());
        assertThat(json(created).at("/data/doctorId").asLong()).isEqualTo(12);
        assertThat(json(created).at("/data/appointmentId").asLong()).isEqualTo(102);
        assertThat(json(created).at("/data/visitDate").asText()).isEqualTo("2026-10-20T02:00:00Z");
        assertThat(EVENTS).containsSubsequence("IDENTITY","CLINICAL:MEDICAL_CREATE","CLINICAL:VALID_VISIT","DOCTOR","APPOINTMENT");
        assertCounts(1,1,1,1); assertThat(DOC.requests).hasSize(1); assertThat(APT.requests).hasSize(1);
        assertThat(jdbc.queryForObject("select actor_id from medical_record_revisions",String.class)).isEqualTo("opaque-doctor-fixture");
        assertThat(jdbc.queryForObject("select count(*) from medical_record_revisions where entity_version=0 and action='CREATE'",Long.class)).isEqualTo(1);
    }
    @Test void walkInStillChecksDoctorAndVisitButNeverCallsAppointment() {
        for(int i=0;i<2;i++) assertThat(request("POST",records(),"doctor",medical(null)).statusCode()).isEqualTo(201);
        assertThat(DOC.calls.get()).isEqualTo(2); assertThat(APT.calls.get()).isZero();
        assertThat(CLINICAL.requests).anySatisfy(q -> {
            assertThat(q.get("operation")).isEqualTo("VALID_VISIT"); assertThat(q.get("appointmentId")).isNull();
        });
        assertCounts(1,1,2,2);
    }
    @ParameterizedTest @ValueSource(strings={"MISSING","INACTIVE"})
    void doctorBusinessFailuresAre422AndStopBeforeAppointment(String mode) {
        DOC.handler=id -> mode.equals("MISSING")?DOC.json(404,missing("DOCTOR_NOT_FOUND")):DOC.json(200,doctor(id,false));
        var before=snapshot(); expect(request("POST",records(),"doctor",medical(102L)),422,"REFERENCE_MISMATCH");
        assertThat(snapshot()).isEqualTo(before); assertThat(APT.calls.get()).isZero();
    }
    @Test void wrongDoctorIdentityOrMalformedActiveIsTechnical503() {
        var before=snapshot();
        for(String mode:List.of("wrong-id","active-missing","active-null")) {
            DOC.handler=id -> {
                var body=doctor(mode.equals("wrong-id")?13:id,true);
                if(mode.equals("active-missing")) data(body).remove("active");
                if(mode.equals("active-null")) data(body).put("active",null);
                return DOC.json(200,body);
            };
            expect(request("POST",records(),"doctor",medical(102L)),503,"DEPENDENCY_UNAVAILABLE");
        }
        assertThat(snapshot()).isEqualTo(before); assertThat(APT.calls.get()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"MISSING","CANCELLED"})
    void missingOrCancelledAppointmentIs422(String mode) {
        APT.handler=id -> mode.equals("MISSING")?APT.json(404,missing("APPOINTMENT_NOT_FOUND")):
                APT.json(200,appointment(id,own,12,"CANCELLED"));
        var before=snapshot(); expect(request("POST",records(),"doctor",medical(102L)),422,"REFERENCE_MISMATCH");
        assertThat(snapshot()).isEqualTo(before); assertThat(DOC.calls.get()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"PATIENT","DOCTOR"})
    void wellFormedReferencePatientOrDoctorMismatchIs422(String mode) {
        APT.handler=id -> APT.json(200,appointment(id,mode.equals("PATIENT")?UUID.randomUUID():own,mode.equals("DOCTOR")?13:12,"CONFIRMED"));
        var before=snapshot(); expect(request("POST",records(),"doctor",medical(102L)),422,"REFERENCE_MISMATCH");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void unknownAppointmentStatusIs503NeverDefaultedToAllowed() {
        APT.handler=id -> APT.json(200,appointment(id,own,12,"PENDING"));
        var before=snapshot(); expect(request("POST",records(),"doctor",medical(102L)),503,"DEPENDENCY_UNAVAILABLE");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void wrongAppointmentIdAndLegacyUnmarked404Are503() {
        var before=snapshot();
        APT.handler=id -> APT.json(200,appointment(103,own,12,"CONFIRMED"));
        expect(request("POST",records(),"doctor",medical(102L)),503,"DEPENDENCY_UNAVAILABLE");
        APT.handler=id -> new TestReferenceServer.Reply(404,"application/json",null,bytes(missing("APPOINTMENT_NOT_FOUND")),null);
        expect(request("POST",records(),"doctor",medical(102L)),503,"DEPENDENCY_UNAVAILABLE");
        assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"DOCTOR","APPOINTMENT"})
    void machineCredentialDenialIs503NotHuman401AndHasNoWrite(String kind) {
        (kind.equals("DOCTOR")?DOC:APT).rejectAuthentication=true;
        var before=snapshot(); var response=request("POST",records(),"doctor",medical(102L));
        expect(response,503,"DEPENDENCY_UNAVAILABLE"); privateError(response);
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void provider5xxDoesNotBecomeBusinessMissingAndNoReferenceCacheHidesOutage() {
        var before=snapshot();
        DOC.handler=id -> DOC.json(503,missing("DOCTOR_NOT_FOUND"));
        expect(request("POST",records(),"doctor",medical(102L)),503,"DEPENDENCY_UNAVAILABLE");
        DOC.handler=id -> DOC.json(200,doctor(id,true)); APT.handler=id -> APT.json(503,missing("APPOINTMENT_NOT_FOUND"));
        expect(request("POST",records(),"doctor",medical(102L)),503,"DEPENDENCY_UNAVAILABLE");
        assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"MEDICAL_CREATE","VALID_VISIT"})
    void clinicalScopeDenialPrecedesReferencesAndMutation(String operation) {
        CLINICAL.handler=(kind,q) -> TestAuthServer.Reply.json(200,ScopeFixtures.bytes(
                operation.equals(q.get("operation"))?ScopeFixtures.deny(q):ScopeFixtures.grant(q,Set.of())));
        var before=snapshot(); expect(request("POST",records(),"doctor",medical(102L)),403,"FORBIDDEN");
        assertThat(DOC.calls.get()).isZero(); assertThat(APT.calls.get()).isZero(); assertThat(snapshot()).isEqualTo(before);
    }
    @Test void bodyDoctorSpoofCannotInvokeReferenceProviders() {
        var wrong=medical(102L); wrong.put("doctorId",13);
        var before=snapshot(); expect(request("POST",records(),"doctor",wrong),403,"FORBIDDEN");
        assertThat(DOC.calls.get()).isZero(); assertThat(APT.calls.get()).isZero(); assertThat(snapshot()).isEqualTo(before);
    }
    @Test void inactiveParentRemainsFinalLocalWriteGuardAfterValidReferences() {
        var replace=new LinkedHashMap<>(profile()); replace.put("gender","UNKNOWN"); replace.put("status","INACTIVE"); replace.put("version",0);
        assertThat(request("PUT","/api/v2/patients/"+own,"staff",replace).statusCode()).isEqualTo(200);
        var before=snapshot(); expect(request("POST",records(),"doctor",medical(102L)),409,"PATIENT_INACTIVE");
        assertThat(snapshot()).isEqualTo(before); assertThat(APT.calls.get()).isEqualTo(1);
    }
    @Test void concurrentLinkedCreatesYield201And409WithOneRecordRevisionAndNoOrphan() throws Exception {
        var barrier=new CyclicBarrier(2);
        APT.handler=id -> {
            try { barrier.await(10,TimeUnit.SECONDS); }
            catch(Exception e) { throw new IllegalStateException("test reference barrier",e); }
            return APT.json(200,appointment(id,own,12,"CONFIRMED"));
        };
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> request("POST",records(),"doctor",medical(102L)));
            var b=pool.submit(() -> request("POST",records(),"doctor",medical(102L)));
            var x=a.get(15,TimeUnit.SECONDS); var y=b.get(15,TimeUnit.SECONDS);
            assertThat(List.of(x.statusCode(),y.statusCode())).containsExactlyInAnyOrder(201,409);
            expect(x.statusCode()==409?x:y,409,"APPOINTMENT_RECORD_EXISTS");
        }
        assertCounts(1,1,1,1);
        assertThat(jdbc.queryForObject("select count(*) from medical_record_revisions r left join medical_records m on r.medical_record_id=m.id where m.id is null",Long.class)).isZero();
        assertThat(DOC.calls.get()).isEqualTo(2); assertThat(APT.calls.get()).isEqualTo(2);
    }
    @Test void revisionFailureAfterAllRealAdapterLookupsRollsBackMedicalDataAndAudit() {
        doThrow(new IllegalStateException("test-only private revision fault")).when(revisionSpy).medical(isNull(),any(),isNull());
        var before=snapshot(); var response=request("POST",records(),"doctor",medical(102L));
        expect(response,500,"INTERNAL_SERVER_ERROR"); privateError(response);
        assertThat(snapshot()).isEqualTo(before); assertThat(APT.calls.get()).isEqualTo(1);
    }
    @Test void medicalPutDoesNotRevalidateImmutableExternalReferences() {
        var created=request("POST",records(),"doctor",medical(null));
        assertThat(created.statusCode()).isEqualTo(201);
        long id=json(created).at("/data/id").asLong();
        DOC.rejectAuthentication=true; APT.rejectAuthentication=true;
        var body=medical(null); body.remove("doctorId"); body.remove("appointmentId"); body.put("version",0); body.put("changeReason","Synthetic review");
        var result=request("PUT",records()+"/"+id,"doctor",body);
        assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
        assertThat(json(result).at("/data/version").asInt()).isZero();
        assertThat(DOC.calls.get()).isEqualTo(1); assertThat(APT.calls.get()).isZero(); assertCounts(1,1,1,1);
    }

    private TestAuthServer.Reply identity(String token) {
        EVENTS.add("IDENTITY"); var data=IdentityFixtures.user(); data.put("patientId",null);
        switch(token) {
            case "staff" -> { data.put("sub","opaque-staff-fixture"); data.put("authorities",List.of("ROLE_STAFF")); }
            case "doctor" -> { data.put("sub","opaque-doctor-fixture"); data.put("doctorId",12); data.put("authorities",List.of("ROLE_DOCTOR")); }
            default -> { return TestAuthServer.Reply.json(200,bytes(Map.of("active",false))); }
        }
        return TestAuthServer.Reply.json(200,bytes(data));
    }
    private String records() { return "/api/v2/patients/"+own+"/medical-records"; }
    private HttpResponse<String> request(String method,String path,String token,Map<String,Object> body) {
        try {
            var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20))
                    .header("Authorization","Bearer "+token);
            if(body!=null) builder.header("Content-Type","application/json");
            return HTTP.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():
                    HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        } catch(Exception e) { throw new AssertionError("Reference TCP request failed",e); }
    }
    private static JsonNode json(HttpResponse<String> r) { return JSON.readTree(r.body()); }
    private static void expect(HttpResponse<String> r,int status,String code) {
        assertThat(r.statusCode()).as(r.body()).isEqualTo(status); var b=json(r);
        assertThat(b.path("status").asInt()).isEqualTo(status); assertThat(b.path("code").asText()).isEqualTo(code);
        assertThat(b.path("message").asText()).isEqualTo(code); assertThat(b.has("data") && b.path("data").isNull()).isTrue();
        assertThat(b.path("fieldErrors").isArray()).isTrue();
    }
    private static void privateError(HttpResponse<String> r) {
        assertThat(r.body()).doesNotContain(AUTH.clientSecret,DOC.secret,APT.secret,ADMIN.adminSecret,CLINICAL.clinicalSecret,
                DOC.collectionUri().toString(),APT.collectionUri().toString(),"Synthetic diagnosis","private revision","stackTrace");
    }
    private void assertCounts(long patients,long patientRevisions,long medical,long medicalRevisions) {
        assertThat(snapshot().subList(0,4)).containsExactly(patients,patientRevisions,medical,medicalRevisions);
    }
    private List<Long> snapshot() {
        return List.of(jdbc.queryForObject("select count(*) from patients",Long.class),
                jdbc.queryForObject("select count(*) from patient_revisions",Long.class),
                jdbc.queryForObject("select count(*) from medical_records",Long.class),
                jdbc.queryForObject("select count(*) from medical_record_revisions",Long.class),
                jdbc.queryForObject("select coalesce(sum(version),0) from patients",Long.class),
                jdbc.queryForObject("select coalesce(sum(version),0) from medical_records",Long.class));
    }
    private static Map<String,Object> profile() { return Map.of("fullName","Reference synthetic","phoneNumber","0900000001"); }
    private static Map<String,Object> medical(Long appointment) {
        var data=new LinkedHashMap<String,Object>(Map.of("doctorId",12,"visitDate","2026-10-20T02:00:00Z",
                "symptoms","Synthetic symptoms","diagnosis","Synthetic diagnosis"));
        data.put("appointmentId",appointment); return data;
    }
    private static EmbeddedPostgres startPostgres() {
        try {
            Path root=Files.createTempDirectory(Path.of("target").toAbsolutePath(),"patient-reference-tcp-it-");
            var pg=EmbeddedPostgres.builder().setPort(0).setDataDirectory(root.resolve("data"))
                    .setOverrideWorkingDirectory(root.resolve("binaries").toFile()).setCleanDataDirectory(true)
                    .setServerConfig("listen_addresses","127.0.0.1").setServerConfig("timezone","UTC")
                    .setServerConfig("log_min_error_statement","panic").start();
            try { new ResourceDatabasePopulator(new ClassPathResource("db/schema/V001__patient_v2_empty.sql"))
                    .execute(pg.getPostgresDatabase()); return pg; }
            catch(Throwable e) { pg.close(); throw e; }
        } catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
}

