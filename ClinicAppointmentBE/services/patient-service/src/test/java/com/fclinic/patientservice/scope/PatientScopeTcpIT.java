package com.fclinic.patientservice.scope;

import com.fclinic.patientservice.application.port.out.*;
import com.fclinic.patientservice.infrastructure.adapter.identity.IntrospectionIdentityVerifier;
import com.fclinic.patientservice.infrastructure.adapter.scope.*;
import com.fclinic.patientservice.identity.*;
import com.fclinic.patientservice.domain.model.PatientError;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import tools.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.fclinic.patientservice.scope.ScopeFixtures.*;
import static com.fclinic.patientservice.application.port.out.WorkScopePort.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Patient TCP + production identity/scope adapters + fresh PG; upstream authorities/references are DOUBLES. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"eureka.client.enabled=false","spring.cloud.discovery.enabled=false","server.address=127.0.0.1"})
@Import(PatientScopeTcpIT.FixedClock.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PatientScopeTcpIT {
    static final TestAuthServer AUTH=new TestAuthServer();
    static final TestScopeServer SCOPE=new TestScopeServer();
    static final EmbeddedPostgres PG=startPostgres();
    static final HttpClient HTTP=HttpClient.newHttpClient();
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",() -> PG.getJdbcUrl("postgres","postgres"));
        r.add("spring.datasource.username",() -> "postgres"); r.add("spring.datasource.password",() -> "postgres");
        r.add("patient.identity.enabled",() -> true); r.add("patient.identity.endpoint",AUTH::endpoint);
        r.add("patient.identity.issuer",() -> "https://identity.example.test");
        r.add("patient.identity.client-id",() -> IdentityFixtures.CLIENT_ID);
        r.add("patient.identity.client-secret",() -> AUTH.clientSecret);
        r.add("patient.identity.trust-store",() -> TestAuthServer.TLS.store);
        r.add("patient.identity.trust-store-password",() -> TestAuthServer.TLS.password);
        r.add("patient.identity.services["+IdentityFixtures.SERVICE_SUBJECT+"].name",() -> "appointment-service");
        r.add("patient.identity.services["+IdentityFixtures.SERVICE_SUBJECT+"].authorities[0]",() -> "SVC_PATIENT_READ");
        r.add("patient.scope.enabled",() -> true);
        for(var kind:ScopeQuery.Authority.values()) {
            boolean admin=kind==ScopeQuery.Authority.ADMINISTRATIVE;
            String prefix="patient.scope."+(admin?"administrative":"clinical")+".";
            r.add(prefix+"endpoint",() -> SCOPE.endpoint(kind));
            r.add(prefix+"client-id",() -> admin?TestScopeServer.ADMIN_ID:TestScopeServer.CLINICAL_ID);
            r.add(prefix+"client-secret",() -> admin?SCOPE.adminSecret:SCOPE.clinicalSecret);
            r.add(prefix+"trust-store",() -> TestAuthServer.TLS.store);
            r.add(prefix+"trust-store-password",() -> TestAuthServer.TLS.password);
            r.add(prefix+"connect-timeout",() -> "500ms"); r.add(prefix+"exchange-timeout",() -> "2s");
        }
    }
    @TestConfiguration static class FixedClock { @Bean @Primary Clock scopeClock() { return CLOCK; } }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityVerifierPort identity;
    @Autowired WorkScopePort scopePort;
    @MockitoSpyBean RemoteWorkScopeAdapter scopeAdapter;
    @MockitoBean DoctorClientPort doctors;
    @MockitoBean AppointmentClientPort appointments;
    UUID own;
    final Set<UUID> authorized=new HashSet<>();
    @BeforeEach void setup() {
        assertThat(identity).isInstanceOf(IntrospectionIdentityVerifier.class);
        assertThat(scopePort).isInstanceOf(RemoteWorkScopeAdapter.class);
        reset(scopeAdapter,doctors,appointments);
        jdbc.execute("TRUNCATE medical_record_revisions,patient_revisions,medical_records,patients RESTART IDENTITY CASCADE");
        authorized.clear(); SCOPE.rejectAuthentication=false;
        SCOPE.handler=(kind,request) -> TestAuthServer.Reply.json(200,bytes(grant(request,authorized)));
        AUTH.handler=this::authReply;
        when(doctors.find(12)).thenReturn(Optional.of(new DoctorClientPort.DoctorReference(12,true)));
        when(appointments.find(anyLong())).thenThrow(new PatientError(PatientError.Code.DEPENDENCY_UNAVAILABLE));
        own=create("Scope synthetic"); authorized.add(own);
        SCOPE.requests.clear(); SCOPE.adminCalls.set(0); SCOPE.clinicalCalls.set(0);
    }
    @AfterAll static void stop() throws Exception { HTTP.close(); AUTH.close(); SCOPE.close(); PG.close(); }

    @Test void staffRoleWithoutAssignmentAndForgedScopeHeadersCannotCreate() throws Exception {
        SCOPE.handler=(kind,q) -> TestAuthServer.Reply.json(200,bytes(deny(q)));
        var before=snapshot();
        var req=HttpRequest.newBuilder(url("/api/v2/patients")).header("Authorization","Bearer staff")
                .header("X-Scope","ALL").header("X-Clinic-Id","1").header("X-Assignment-Id","forged")
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(profile("Forged")))).build();
        expect(HTTP.send(req,HttpResponse.BodyHandlers.ofString()),403,"FORBIDDEN");
        assertThat(snapshot()).isEqualTo(before);
        assertThat(SCOPE.requests.getFirst().keySet()).doesNotContain("X-Scope","clinicId");
    }
    @Test void administrativeObjectGrantCannotReadOrUpdateAnotherPatient() {
        UUID other=create("Outside scope");
        SCOPE.handler=(kind,q) -> TestAuthServer.Reply.json(200,bytes(other.toString().equals(q.get("patientId"))?deny(q):grant(q,authorized)));
        var before=snapshot();
        expect(request("GET","/api/v2/patients/"+other,"staff",null),403,"FORBIDDEN");
        expect(request("PUT","/api/v2/patients/"+other,"staff",replacement("Outside scope")),403,"FORBIDDEN");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void searchDenialEmptyAndBoundedScopePreserveCountsAndAndFilters() {
        UUID excluded=create("Outside scope"); authorized.clear();
        SCOPE.handler=(kind,q) -> TestAuthServer.Reply.json(200,bytes("ADMIN_SEARCH".equals(q.get("operation"))?deny(q):grant(q,authorized)));
        expect(request("GET","/api/v2/patients","staff",null),403,"FORBIDDEN");
        SCOPE.handler=(kind,q) -> TestAuthServer.Reply.json(200,bytes(grant(q,authorized)));
        var empty=request("GET","/api/v2/patients","staff",null);
        assertThat(empty.statusCode()).isEqualTo(200); assertThat(json(empty).at("/data/totalElements").asInt()).isZero();
        authorized.add(own);
        var result=request("GET","/api/v2/patients?keyword=Scope&phoneNumber=0900000001&size=1","staff",null);
        assertThat(result.statusCode()).isEqualTo(200); assertThat(json(result).at("/data/totalElements").asInt()).isEqualTo(1);
        assertThat(result.body()).doesNotContain(excluded.toString(),"Outside scope");
        assertThat(json(request("GET","/api/v2/patients?keyword=absent&phoneNumber=0900000001","staff",null))
                .at("/data/totalElements").asInt()).isZero();
        assertThat(json(request("GET","/api/v2/patients?keyword=Scope&phoneNumber=0900000002","staff",null))
                .at("/data/totalElements").asInt()).isZero();
    }
    @Test void doctorSearchUsesClinicalAuthorityAndCannotExpandScope() {
        var result=request("GET","/api/v2/patients","doctor",null);
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(json(result).at("/data/totalElements").asInt()).isEqualTo(1);
        assertThat(SCOPE.adminCalls.get()).isZero();
        assertThat(SCOPE.requests).allSatisfy(q -> assertThat(q.get("operation")).isEqualTo("DOCTOR_SEARCH"));
    }
    @Test void revocationBeforeNoOpAndMutationKeepsVersionsAndRevisions() {
        var before=snapshot();
        SCOPE.handler=(kind,q) -> TestAuthServer.Reply.json(200,bytes(deny(q)));
        expect(request("PUT","/api/v2/patients/"+own,"staff",replacement("Scope synthetic")),403,"FORBIDDEN");
        expect(request("PUT","/api/v2/patients/"+own,"staff",replacement("Changed")),403,"FORBIDDEN");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void currentGrantIsRecheckedBetweenControllerAndUseCaseBeforeWriter() {
        var checks=new AtomicInteger(); var before=snapshot();
        SCOPE.handler=(kind,q) -> TestAuthServer.Reply.json(200,bytes("ADMIN_PROFILE_UPDATE".equals(q.get("operation"))
                && checks.incrementAndGet()>1?deny(q):grant(q,authorized)));
        expect(request("PUT","/api/v2/patients/"+own,"staff",replacement("Changed")),403,"FORBIDDEN");
        assertThat(checks.get()).isEqualTo(2); assertThat(snapshot()).isEqualTo(before);
    }
    @Test void clinicalReadCreateUpdateRemainDistinctAndAuthorStillRequired() {
        SCOPE.handler=(kind,q) -> TestAuthServer.Reply.json(200,bytes("MEDICAL_UPDATE".equals(q.get("operation"))?deny(q):grant(q,authorized)));
        long record=createMedical(null);
        var before=snapshot();
        assertThat(request("GET","/api/v2/patients/"+own+"/medical-records/"+record,"doctor",null).statusCode()).isEqualTo(200);
        expect(request("PUT","/api/v2/patients/"+own+"/medical-records/"+record,"doctor",medicalReplacement()),403,"FORBIDDEN");
        SCOPE.handler=(kind,q) -> TestAuthServer.Reply.json(200,bytes(grant(q,authorized)));
        expect(request("PUT","/api/v2/patients/"+own+"/medical-records/"+record,"other-doctor",medicalReplacement()),403,"FORBIDDEN");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void revokedClinicalAssignmentAndValidVisitDenyBothLinkedAndWalkIn() {
        for(String denied:List.of("MEDICAL_CREATE","VALID_VISIT")) {
            SCOPE.handler=(kind,q) -> TestAuthServer.Reply.json(200,bytes(denied.equals(q.get("operation"))?deny(q):grant(q,authorized)));
            var before=snapshot();
            expect(request("POST","/api/v2/patients/"+own+"/medical-records","doctor",medical(null)),403,"FORBIDDEN");
            expect(request("POST","/api/v2/patients/"+own+"/medical-records","doctor",medical(102L)),403,"FORBIDDEN");
            assertThat(snapshot()).isEqualTo(before); verifyNoInteractions(doctors,appointments);
        }
    }
    @Test void walkInRequiresVisitEvidenceButSkipsAppointmentAndWritesAtomicRevision() {
        long record=createMedical(null); verifyNoInteractions(appointments);
        assertThat(SCOPE.requests).anySatisfy(q -> {
            assertThat(q.get("operation")).isEqualTo("VALID_VISIT"); assertThat(q.get("appointmentId")).isNull();
            assertThat(nested(q,"caller").get("doctorId").toString()).isEqualTo("12");
        });
        assertThat(jdbc.queryForObject("select created_by from medical_records where id=?",String.class,record)).isEqualTo(DOCTOR.subject());
        assertThat(jdbc.queryForObject("select count(*) from medical_record_revisions",Long.class)).isEqualTo(1);
    }
    @Test void linkedScopeGrantCannotSubstituteForUnavailableAppointmentReference() {
        var before=snapshot();
        expect(request("POST","/api/v2/patients/"+own+"/medical-records","doctor",medical(102L)),503,"DEPENDENCY_UNAVAILABLE");
        assertThat(snapshot()).isEqualTo(before); verify(appointments).find(102);
        assertThat(SCOPE.requests).anySatisfy(q -> {
            assertThat(q.get("operation")).isEqualTo("VALID_VISIT");
            assertThat(q.get("appointmentId").toString()).isEqualTo("102");
        });
    }
    @Test void linkedWithReferenceDoubleKeepsDuplicateGuardAndNestedParent() {
        doReturn(Optional.of(new AppointmentClientPort.AppointmentReference(102,own,12,false))).when(appointments).find(102);
        long record=createMedical(102L); var before=snapshot();
        expect(request("POST","/api/v2/patients/"+own+"/medical-records","doctor",medical(102L)),409,"APPOINTMENT_RECORD_EXISTS");
        assertThat(snapshot()).isEqualTo(before);
        UUID other=create("Other synthetic");
        expect(request("GET","/api/v2/patients/"+other+"/medical-records/"+record,"doctor",null),404,"MEDICAL_RECORD_NOT_FOUND");
    }
    @Test void wrongDoctorBodyAndStaffAdminNeverAcquireClinicalRights() {
        var before=snapshot();
        for(String token:List.of("staff","admin","doctor-unlinked"))
            expect(request("POST","/api/v2/patients/"+own+"/medical-records",token,medical(null)),403,"FORBIDDEN");
        var wrong=medical(null); wrong.put("doctorId",13);
        expect(request("POST","/api/v2/patients/"+own+"/medical-records","doctor",wrong),403,"FORBIDDEN");
        assertThat(snapshot()).isEqualTo(before); verifyNoInteractions(doctors,appointments);
    }
    @Test void misboundPrincipalPatientDoctorAndForgedPayloadAre503NoWrites() {
        var before=snapshot();
        for(String field:List.of("subject","doctorId","patientId","unexpected")) {
            SCOPE.handler=(kind,q) -> {
                var result=grant(q,authorized);
                if(field.equals("unexpected")) result.put("scope","ALL");
                else if(field.equals("patientId")) { var echo=new LinkedHashMap<>(q); echo.put("patientId",UUID.randomUUID().toString()); result.put("request",echo); }
                else { var echo=new LinkedHashMap<>(q); var caller=new LinkedHashMap<>(nested(q,"caller")); caller.put(field,"forged"); echo.put("caller",caller); result.put("request",echo); }
                return TestAuthServer.Reply.json(200,bytes(result));
            };
            expect(request("POST","/api/v2/patients/"+own+"/medical-records","doctor",medical(null)),503,"DEPENDENCY_UNAVAILABLE");
        }
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void expiredOrInactiveAssignmentIs403NotTechnicalFailure() {
        var before=snapshot();
        for(boolean active:new boolean[]{true,false}) {
            SCOPE.handler=(kind,q) -> {
                var result=grant(q,authorized); var evidence=nested(result,"evidence");
                evidence.put("active",active);
                if(active) { evidence.put("validFrom",CLOCK.instant().minusSeconds(100).toString()); evidence.put("validUntil",CLOCK.instant().toString()); }
                return TestAuthServer.Reply.json(200,bytes(result));
            };
            expect(request("POST","/api/v2/patients","staff",profile("Denied")),403,"FORBIDDEN");
        }
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void technicalCredentialFailureOutageAndMalformedPayloadAre503WithoutLeak() {
        var before=snapshot();
        SCOPE.rejectAuthentication=true;
        expect(request("POST","/api/v2/patients","staff",profile("Denied")),503,"DEPENDENCY_UNAVAILABLE");
        SCOPE.rejectAuthentication=false;
        for(var reply:new TestAuthServer.Reply[]{
                TestAuthServer.Reply.json(503,bytes(Map.of("error","private-scope-detail"))),
                TestAuthServer.Reply.json(200,"not-json".getBytes()),
                TestAuthServer.Reply.json(200,"x".repeat(262145).getBytes())}) {
            SCOPE.handler=(kind,q) -> reply;
            var response=request("POST","/api/v2/patients","staff",profile("Denied"));
            expect(response,503,"DEPENDENCY_UNAVAILABLE");
            assertThat(response.body()).doesNotContain("private-scope-detail",SCOPE.adminSecret,SCOPE.clinicalSecret,
                    AUTH.clientSecret,SCOPE.endpoint(ScopeQuery.Authority.ADMINISTRATIVE).toString(),"stackTrace");
        }
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void scopeTimeout503HasNoWriteUsingLatch() throws Exception {
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1); var before=snapshot();
        SCOPE.handler=(kind,q) -> {
            entered.countDown();
            try { release.await(10,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            return TestAuthServer.Reply.json(200,bytes(grant(q,authorized)));
        };
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var result=pool.submit(() -> request("POST","/api/v2/patients","staff",profile("Denied")));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            expect(result.get(6,TimeUnit.SECONDS),503,"DEPENDENCY_UNAVAILABLE");
        } finally { release.countDown(); }
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void f06UntypedScopeFaultIs503AndNoWrite() {
        var before=snapshot();
        doThrow(new IllegalStateException("private-scope-fault")).when(scopeAdapter).receptionist(any(),any(AdministrativeAction.class));
        expect(request("POST","/api/v2/patients","staff",profile("Denied")),503,"DEPENDENCY_UNAVAILABLE");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void medicalNoOpThenRevocationKeepsDataVersionAndRevision() {
        long id=createMedical(null);
        var noop=request("PUT","/api/v2/patients/"+own+"/medical-records/"+id,"doctor",medicalReplacement());
        assertThat(noop.statusCode()).as(noop.body()).isEqualTo(200);
        assertThat(json(noop).at("/data/version").asInt()).isZero(); var before=snapshot();
        SCOPE.handler=(kind,q) -> TestAuthServer.Reply.json(200,bytes(deny(q)));
        expect(request("PUT","/api/v2/patients/"+own+"/medical-records/"+id,"doctor",medicalReplacement()),403,"FORBIDDEN");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void serviceProfileLookupNeedsAdministrativeGrantAndNeverClinical() {
        assertThat(request("GET","/api/v2/patients/"+own,"service",null).statusCode()).isEqualTo(200);
        assertThat(SCOPE.requests).allSatisfy(q -> assertThat(q.get("operation")).isEqualTo("APPOINTMENT_PROFILE_READ"));
        SCOPE.handler=(kind,q) -> TestAuthServer.Reply.json(200,bytes(deny(q)));
        expect(request("GET","/api/v2/patients/"+own,"service",null),403,"FORBIDDEN");
        expect(request("GET","/api/v2/patients/"+own+"/medical-records","service",null),403,"FORBIDDEN");
    }
    private TestAuthServer.Reply authReply(String token) {
        var data=IdentityFixtures.user(); data.put("patientId",null);
        switch(token) {
            case "staff" -> { data.put("sub",STAFF.subject()); data.put("authorities",List.of("ROLE_STAFF")); }
            case "doctor","other-doctor","doctor-unlinked" -> {
                data.put("sub",token.equals("other-doctor")?"opaque-other-doctor-fixture":DOCTOR.subject());
                data.put("authorities",List.of("ROLE_DOCTOR"));
                data.put("doctorId",token.equals("doctor-unlinked")?null:token.equals("other-doctor")?13L:12L);
            }
            case "admin" -> data.put("authorities",List.of("ROLE_ADMIN"));
            case "service" -> data=IdentityFixtures.service();
            default -> { return TestAuthServer.Reply.json(200,bytes(Map.of("active",false))); }
        }
        return TestAuthServer.Reply.json(200,bytes(data));
    }
    private UUID create(String name) {
        var response=request("POST","/api/v2/patients","staff",profile(name));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return UUID.fromString(json(response).at("/data/id").asText());
    }
    private long createMedical(Long appointment) {
        var response=request("POST","/api/v2/patients/"+own+"/medical-records","doctor",medical(appointment));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json(response).at("/data/id").asLong();
    }
    private HttpResponse<String> request(String method,String path,String token,Map<String,Object> body) {
        try {
            var builder=HttpRequest.newBuilder(url(path)).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+token);
            if(body!=null) builder.header("Content-Type","application/json");
            return HTTP.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():
                    HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        } catch(Exception e) { throw new AssertionError("Scope TCP request failed",e); }
    }
    private URI url(String path) { return URI.create("http://127.0.0.1:"+port+path); }
    private static JsonNode json(HttpResponse<String> r) { return JSON.readTree(r.body()); }
    private static void expect(HttpResponse<String> r,int status,String code) {
        assertThat(r.statusCode()).as(r.body()).isEqualTo(status); var b=json(r);
        assertThat(b.path("status").asInt()).isEqualTo(status); assertThat(b.path("code").asText()).isEqualTo(code);
        assertThat(b.path("message").asText()).isEqualTo(code); assertThat(b.has("data") && b.path("data").isNull()).isTrue();
        assertThat(b.path("fieldErrors").isArray()).isTrue();
    }
    private List<Long> snapshot() {
        return List.of(jdbc.queryForObject("select count(*) from patients",Long.class),
                jdbc.queryForObject("select count(*) from medical_records",Long.class),
                jdbc.queryForObject("select count(*) from patient_revisions",Long.class),
                jdbc.queryForObject("select count(*) from medical_record_revisions",Long.class),
                jdbc.queryForObject("select coalesce(sum(version),0) from patients",Long.class),
                jdbc.queryForObject("select coalesce(sum(version),0) from medical_records",Long.class));
    }
    private static Map<String,Object> profile(String name) { return Map.of("fullName",name,"phoneNumber","0900000001"); }
    private static Map<String,Object> replacement(String name) {
        return new LinkedHashMap<>(Map.of("fullName",name,"phoneNumber","0900000001","gender","UNKNOWN","status","ACTIVE","version",0));
    }
    private static Map<String,Object> medical(Long appointment) {
        var body=new LinkedHashMap<String,Object>(Map.of("doctorId",12,"visitDate","2026-10-20T02:00:00Z",
                "symptoms","Scope simulated symptoms","diagnosis","Scope simulated diagnosis"));
        body.put("appointmentId",appointment); return body;
    }
    private static Map<String,Object> medicalReplacement() {
        var b=medical(null); b.remove("doctorId"); b.remove("appointmentId"); b.put("version",0); b.put("changeReason","Synthetic review"); return b;
    }
    private static EmbeddedPostgres startPostgres() {
        try {
            Path root=Files.createTempDirectory(Path.of("target").toAbsolutePath(),"patient-scope-tcp-it-");
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

