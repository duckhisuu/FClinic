package com.fclinic.patientservice.identity;

import com.fclinic.patientservice.application.port.out.*;
import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.infrastructure.adapter.identity.IntrospectionIdentityVerifier;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.fclinic.patientservice.identity.IdentityFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual Patient TCP + production HTTPS adapter + fresh PostgreSQL. Auth/scope/reference providers are test doubles. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"eureka.client.enabled=false","spring.cloud.discovery.enabled=false","server.address=127.0.0.1"})
@Import(PatientIdentityTcpIT.FixedClock.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PatientIdentityTcpIT {
    static final TestAuthServer AUTH=new TestAuthServer();
    static final EmbeddedPostgres PG=startPostgres();
    static final HttpClient HTTP=HttpClient.newHttpClient();
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",() -> PG.getJdbcUrl("postgres","postgres"));
        r.add("spring.datasource.username",() -> "postgres"); r.add("spring.datasource.password",() -> "postgres");
        r.add("patient.identity.enabled",() -> true);
        r.add("patient.identity.endpoint",AUTH::endpoint);
        r.add("patient.identity.issuer",() -> "https://identity.example.test");
        r.add("patient.identity.client-id",() -> CLIENT_ID);
        r.add("patient.identity.client-secret",() -> AUTH.clientSecret);
        r.add("patient.identity.trust-store",() -> TestAuthServer.TLS.store);
        r.add("patient.identity.trust-store-password",() -> TestAuthServer.TLS.password);
        r.add("patient.identity.services["+SERVICE_SUBJECT+"].name",() -> "appointment-service");
        r.add("patient.identity.services["+SERVICE_SUBJECT+"].authorities[0]",() -> "SVC_PATIENT_READ");
        r.add("patient.identity.exchange-timeout",() -> "3s");
    }
    @TestConfiguration static class FixedClock { @Bean @Primary Clock tcpClock() { return CLOCK; } }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityVerifierPort identities;
    @MockitoBean(answers=org.mockito.Answers.CALLS_REAL_METHODS) WorkScopePort scopes;
    @MockitoBean DoctorClientPort doctors;
    @MockitoBean AppointmentClientPort appointments;
    UUID own;
    @BeforeEach void setup() {
        assertThat(identities).isInstanceOf(IntrospectionIdentityVerifier.class);
        jdbc.execute("TRUNCATE medical_record_revisions,patient_revisions,medical_records,patients RESTART IDENTITY CASCADE");
        reset(scopes,doctors,appointments);
        when(scopes.receptionist(any())).thenAnswer(i -> ((TrustedCaller)i.getArgument(0)).userRole("ROLE_STAFF"));
        when(scopes.profile(any(),any())).thenReturn(true);
        when(scopes.clinical(any(),any())).thenReturn(true);
        when(scopes.validVisit(any(),any(),any(),any())).thenReturn(true);
        when(scopes.search(any())).thenReturn(new WorkScopePort.SearchScope(Set.of()));
        when(scopes.appointmentProfileLookup(any(),any())).thenAnswer(i -> {
            TrustedCaller caller=i.getArgument(0); return caller.authorities().contains("SVC_PATIENT_READ");
        });
        when(doctors.find(12)).thenReturn(Optional.of(new DoctorClientPort.DoctorReference(12,true)));
        AUTH.handler=this::reply;
        own=UUID.fromString(json(request("POST","/api/v2/patients","staff",profile())).at("/data/id").asText());
        AUTH.calls.set(0); AUTH.authenticatedCalls.set(0);
    }
    @AfterAll static void stop() throws Exception { HTTP.close(); AUTH.close(); PG.close(); }

    @Test void missingMalformedAndDuplicateBearerCannotUseForgedHeaders() throws Exception {
        var before=snapshot();
        for(String header:List.of("","Basic token","Bearer x,y","Bearer x=y","Bearer  token")) {
            var builder=HttpRequest.newBuilder(url("/api/v2/patients")).header("X-User-Id","401")
                    .header("X-Role","ROLE_STAFF").header("X-Patient-Id",own.toString());
            if(!header.isEmpty()) builder.header("Authorization",header);
            expect(HTTP.send(builder.GET().build(),HttpResponse.BodyHandlers.ofString()),401,"UNAUTHENTICATED");
        }
        var duplicate=HttpRequest.newBuilder(url("/api/v2/patients")).header("Authorization","Bearer staff")
                .header("Authorization","Bearer patient").GET().build();
        expect(HTTP.send(duplicate,HttpResponse.BodyHandlers.ofString()),401,"UNAUTHENTICATED");
        assertThat(AUTH.calls.get()).isZero(); assertThat(snapshot()).isEqualTo(before);
    }
    @Test void invalidExpiredAndInactiveCredentialsAre401AndCannotWrite() {
        var before=snapshot();
        for(String token:List.of("invalid","inactive","expired"))
            expect(request("POST","/api/v2/patients",token,profile()),401,"UNAUTHENTICATED");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void issuerAndAudienceMismatchAre401() {
        var before=snapshot();
        for(String token:List.of("wrong-issuer","wrong-audience"))
            expect(request("POST","/api/v2/patients",token,profile()),401,"UNAUTHENTICATED");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void backendCredentialFailureOutageAndBadPayloadAre503WithoutDisclosure() {
        var before=snapshot();
        for(String token:List.of("backend-401","backend-403","outage","bad-payload","forged-service")) {
            var result=request("POST","/api/v2/patients",token,profile());
            expect(result,503,"DEPENDENCY_UNAVAILABLE");
            assertThat(result.body()).doesNotContain(AUTH.clientSecret,AUTH.endpoint().toString(),"private-provider-detail","stackTrace");
        }
        assertThat(snapshot()).isEqualTo(before); assertThat(AUTH.calls.get()).isEqualTo(5);
    }
    @Test void verifiedSelfReadsProfileAndMedicalButNotOtherPatientSearchOrWrites() {
        assertThat(request("GET","/api/v2/patients/"+own,"patient",null).statusCode()).isEqualTo(200);
        var medical=request("GET","/api/v2/patients/"+own+"/medical-records","patient",null);
        assertThat(medical.statusCode()).isEqualTo(200);
        assertThat(json(medical).at("/data/totalElements").asInt()).isZero();
        var before=snapshot();
        expect(request("GET","/api/v2/patients/"+UUID.randomUUID(),"patient",null),403,"FORBIDDEN");
        expect(request("GET","/api/v2/patients","patient",null),403,"FORBIDDEN");
        expect(request("POST","/api/v2/patients","patient",profile()),403,"FORBIDDEN");
        expect(request("GET","/api/v2/patients/"+own,"unlinked",null),403,"FORBIDDEN");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void serviceIsLimitedToPermittedProfileDetailAndNeverHumanPermissions() {
        assertThat(request("GET","/api/v2/patients/"+own,"service",null).statusCode()).isEqualTo(200);
        var before=snapshot();
        expect(request("GET","/api/v2/patients","service",null),403,"FORBIDDEN");
        expect(request("POST","/api/v2/patients","service",profile()),403,"FORBIDDEN");
        expect(request("GET","/api/v2/patients/"+own+"/medical-records","service",null),403,"FORBIDDEN");
        for(String token:List.of("unknown-service","excess-service-authority"))
            expect(request("GET","/api/v2/patients/"+own,token,null),403,"FORBIDDEN");
        doReturn(false).when(scopes).appointmentProfileLookup(any(),any());
        expect(request("GET","/api/v2/patients/"+own,"service",null),403,"FORBIDDEN");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void staffAdminAndMissingDoctorMappingNeverAcquireClinicalWriteAccess() {
        var before=snapshot();
        for(String token:List.of("staff","admin","doctor-unlinked"))
            expect(request("POST","/api/v2/patients/"+own+"/medical-records",token,medical()),403,"FORBIDDEN");
        assertThat(snapshot()).isEqualTo(before); verifyNoInteractions(doctors,appointments);
    }
    @Test void currentScopeIsStillRequiredAfterSuccessfulAuthentication() {
        doReturn(false).when(scopes).receptionist(any());
        when(scopes.clinical(any(),any())).thenReturn(false);
        var before=snapshot();
        expect(request("POST","/api/v2/patients","staff",profile()),403,"FORBIDDEN");
        expect(request("POST","/api/v2/patients/"+own+"/medical-records","doctor",medical()),403,"FORBIDDEN");
        assertThat(snapshot()).isEqualTo(before); verifyNoInteractions(doctors,appointments);
    }
    @Test void verifiedDoctorCanWriteWithTestScopeAndAuditUsesTrustedSubject() {
        var created=request("POST","/api/v2/patients/"+own+"/medical-records","doctor",medical());
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        long id=json(created).at("/data/id").asLong();
        assertThat(jdbc.queryForObject("select created_by from medical_records where id=?",String.class,id))
                .isEqualTo("opaque-doctor-fixture");
        assertThat(jdbc.queryForObject("select count(*) from medical_record_revisions",Integer.class)).isEqualTo(1);
        assertThat(request("GET","/api/v2/patients/"+own+"/medical-records/"+id,"doctor",null).statusCode()).isEqualTo(200);
        UUID other=UUID.fromString(json(request("POST","/api/v2/patients","staff",profile())).at("/data/id").asText());
        var before=snapshot();
        expect(request("GET","/api/v2/patients/"+other+"/medical-records/"+id,"doctor",null),404,"MEDICAL_RECORD_NOT_FOUND");
        var edited=new LinkedHashMap<>(medical()); edited.remove("doctorId"); edited.put("version",0); edited.put("changeReason","Synthetic review");
        expect(request("PUT","/api/v2/patients/"+own+"/medical-records/"+id,"other-doctor",edited),403,"FORBIDDEN");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void profilePutKeepsNoOpVersionAndStaleConflictWithRealIdentityTransport() {
        var replace=new LinkedHashMap<>(profile()); replace.put("gender","UNKNOWN"); replace.put("status","ACTIVE"); replace.put("version",0);
        var noop=request("PUT","/api/v2/patients/"+own,"staff",replace);
        assertThat(noop.statusCode()).isEqualTo(200); assertThat(json(noop).at("/data/version").asInt()).isZero();
        replace.put("fullName","Synthetic changed");
        var changed=request("PUT","/api/v2/patients/"+own,"staff",replace);
        assertThat(changed.statusCode()).isEqualTo(200); assertThat(json(changed).at("/data/version").asInt()).isEqualTo(1);
        expect(request("PUT","/api/v2/patients/"+own,"staff",replace),409,"VERSION_CONFLICT");
        assertThat(jdbc.queryForObject("select count(*) from patient_revisions",Integer.class)).isEqualTo(2);
    }
    @Test void providerActivationChangeIsEffectiveOnNextTcpRequest() {
        assertThat(request("GET","/api/v2/patients/"+own,"patient",null).statusCode()).isEqualTo(200);
        AUTH.handler=t -> TestAuthServer.Reply.json(200,bytes(Map.of("active",false)));
        expect(request("GET","/api/v2/patients/"+own,"patient",null),401,"UNAUTHENTICATED");
        assertThat(AUTH.calls.get()).isEqualTo(2);
    }
    @Test void untrustedBrowserHeadersCannotUpgradeVerifiedUserToStaff() throws Exception {
        var before=snapshot();
        var req=HttpRequest.newBuilder(url("/api/v2/patients")).header("Authorization","Bearer patient")
                .header("X-Role","ROLE_STAFF").header("X-User-Id","401").header("X-Service-Name","appointment-service")
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(profile()))).build();
        expect(HTTP.send(req,HttpResponse.BodyHandlers.ofString()),403,"FORBIDDEN");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void invalidAuthContractProtectsEveryMutationAndAllPersistedContent() {
        long recordId=json(request("POST","/api/v2/patients/"+own+"/medical-records","doctor",medical()))
                .at("/data/id").asLong();
        assertThat(recordId).isPositive();
        var profilePut=new LinkedHashMap<>(profile());
        profilePut.put("gender","UNKNOWN"); profilePut.put("status","ACTIVE"); profilePut.put("version",0);
        var medicalPut=new LinkedHashMap<>(medical()); medicalPut.remove("doctorId");
        medicalPut.put("version",0); medicalPut.put("changeReason","Synthetic contract regression");
        var wrongVersion=user(); wrongVersion.put("contractVersion","patient-identity-introspection/unsupported");
        var missingField=user(); missingField.remove("patientId");
        var replies=List.of(TestAuthServer.Reply.json(200,bytes(wrongVersion)),
                TestAuthServer.Reply.json(200,bytes(missingField)),
                TestAuthServer.Reply.json(200,"not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var before=contentSnapshot(); clearInvocations(scopes,doctors,appointments);
        for(var reply:replies) {
            AUTH.handler=token -> reply;
            expect(request("POST","/api/v2/patients","staff",profile()),503,"DEPENDENCY_UNAVAILABLE");
            expect(request("PUT","/api/v2/patients/"+own,"staff",profilePut),503,"DEPENDENCY_UNAVAILABLE");
            expect(request("POST","/api/v2/patients/"+own+"/medical-records","doctor",medical()),503,"DEPENDENCY_UNAVAILABLE");
            expect(request("PUT","/api/v2/patients/"+own+"/medical-records/"+recordId,"doctor",medicalPut),503,"DEPENDENCY_UNAVAILABLE");
        }
        assertThat(contentSnapshot()).isEqualTo(before);
        verifyNoInteractions(scopes,doctors,appointments);
    }

    @Test void inactiveIntrospectionBlocksPreviouslyAllowedNoOpAndChangedPuts() {
        long recordId=json(request("POST","/api/v2/patients/"+own+"/medical-records","doctor",medical()))
                .at("/data/id").asLong();
        assertThat(recordId).isPositive();
        var profilePut=new LinkedHashMap<>(profile());
        profilePut.put("gender","UNKNOWN"); profilePut.put("status","ACTIVE"); profilePut.put("version",0);
        var medicalPut=new LinkedHashMap<>(medical()); medicalPut.remove("doctorId");
        medicalPut.put("version",0); medicalPut.put("changeReason","Synthetic revocation regression");
        assertThat(request("PUT","/api/v2/patients/"+own,"staff",profilePut).statusCode()).isEqualTo(200);
        assertThat(request("PUT","/api/v2/patients/"+own+"/medical-records/"+recordId,"doctor",medicalPut).statusCode()).isEqualTo(200);
        var before=contentSnapshot(); clearInvocations(scopes,doctors,appointments); AUTH.calls.set(0);
        // Test-only authority transition models the wire result of revocation; it proves no token lifecycle/signature.
        AUTH.handler=token -> TestAuthServer.Reply.json(200,bytes(Map.of("active",false)));
        expect(request("PUT","/api/v2/patients/"+own,"staff",profilePut),401,"UNAUTHENTICATED");
        profilePut.put("fullName","Synthetic rejected change");
        expect(request("PUT","/api/v2/patients/"+own,"staff",profilePut),401,"UNAUTHENTICATED");
        expect(request("PUT","/api/v2/patients/"+own+"/medical-records/"+recordId,"doctor",medicalPut),401,"UNAUTHENTICATED");
        medicalPut.put("diagnosis","Synthetic rejected diagnosis");
        expect(request("PUT","/api/v2/patients/"+own+"/medical-records/"+recordId,"doctor",medicalPut),401,"UNAUTHENTICATED");
        assertThat(AUTH.calls.get()).isEqualTo(4);
        assertThat(contentSnapshot()).isEqualTo(before);
        verifyNoInteractions(scopes,doctors,appointments);
    }

    @Test void authDeadlinePreventsAdministrativeAndClinicalWrites() throws Exception {
        var entered=new CountDownLatch(2); var release=new CountDownLatch(1);
        var before=contentSnapshot(); clearInvocations(scopes,doctors,appointments);
        AUTH.handler=token -> {
            entered.countDown();
            try { release.await(8,TimeUnit.SECONDS); }
            catch(InterruptedException error) { Thread.currentThread().interrupt(); }
            return TestAuthServer.Reply.json(200,bytes(user()));
        };
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var administrative=pool.submit(() -> request("POST","/api/v2/patients","staff",profile()));
            var clinical=pool.submit(() -> request("POST","/api/v2/patients/"+own+"/medical-records","doctor",medical()));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            expect(administrative.get(6,TimeUnit.SECONDS),503,"DEPENDENCY_UNAVAILABLE");
            expect(clinical.get(6,TimeUnit.SECONDS),503,"DEPENDENCY_UNAVAILABLE");
        } finally { release.countDown(); }
        assertThat(contentSnapshot()).isEqualTo(before);
        verifyNoInteractions(scopes,doctors,appointments);
    }

    private List<List<String>> contentSnapshot() {
        return List.of("patients","medical_records","patient_revisions","medical_record_revisions").stream()
                .map(table -> jdbc.queryForList("select row_to_json(t)::text from "+table+" t order by id",String.class))
                .toList();
    }

    private TestAuthServer.Reply reply(String token) {
        if(Set.of("invalid","inactive").contains(token)) return TestAuthServer.Reply.json(200,bytes(Map.of("active",false)));
        if(token.equals("backend-401")) return TestAuthServer.Reply.json(401,bytes(Map.of("error","private-provider-detail")));
        if(token.equals("backend-403")) return TestAuthServer.Reply.json(403,bytes(Map.of("error","private-provider-detail")));
        if(token.equals("outage")) return TestAuthServer.Reply.json(503,bytes(Map.of("error","private-provider-detail")));
        if(token.equals("bad-payload")) return TestAuthServer.Reply.json(200,"not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var data=user(); data.put("patientId",own==null?PATIENT.toString():own.toString());
        switch(token) {
            case "staff" -> { data.put("sub","opaque-staff-fixture"); data.put("authorities",List.of("ROLE_STAFF")); data.put("patientId",null); }
            case "admin" -> { data.put("authorities",List.of("ROLE_ADMIN")); data.put("patientId",null); }
            case "doctor", "other-doctor", "doctor-unlinked" -> {
                data.put("sub",token.equals("other-doctor")?"opaque-other-doctor-fixture":"opaque-doctor-fixture");
                data.put("authorities",List.of("ROLE_DOCTOR")); data.put("patientId",null);
                data.put("doctorId",token.equals("doctor-unlinked")?null:token.equals("other-doctor")?13L:12L);
            }
            case "service" -> data=service();
            case "unknown-service" -> { data=service(); data.put("sub","appointment-service"); }
            case "excess-service-authority" -> { data=service(); data.put("authorities",List.of("SVC_PATIENT_WRITE")); }
            case "unlinked" -> data.put("patientId",null);
            case "expired" -> data.put("exp",CLOCK.instant().minusSeconds(1).getEpochSecond());
            case "wrong-issuer" -> data.put("iss","https://wrong.example.test");
            case "wrong-audience" -> data.put("aud","other-service");
            case "forged-service" -> { data=service(); data.put("serviceName","appointment-service"); }
            case "patient" -> { }
            default -> { return TestAuthServer.Reply.json(200,bytes(Map.of("active",false))); }
        }
        return TestAuthServer.Reply.json(200,bytes(data));
    }
    private HttpResponse<String> request(String method,String path,String token,Map<String,Object> body) {
        try {
            var builder=HttpRequest.newBuilder(url(path)).timeout(Duration.ofSeconds(10))
                    .header("Authorization","Bearer "+token);
            if(body!=null) builder.header("Content-Type","application/json");
            return HTTP.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():
                    HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        } catch(Exception e) { throw new AssertionError("TCP request failed",e); }
    }
    private URI url(String path) { return URI.create("http://127.0.0.1:"+port+path); }
    private static JsonNode json(HttpResponse<String> response) { return JSON.readTree(response.body()); }
    private static void expect(HttpResponse<String> response,int status,String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        var body=json(response);
        assertThat(body.path("status").asInt()).isEqualTo(status); assertThat(body.path("code").asText()).isEqualTo(code);
        assertThat(body.path("message").asText()).isEqualTo(code); assertThat(body.has("data") && body.path("data").isNull()).isTrue();
        assertThat(body.path("fieldErrors").isArray()).isTrue();
    }
    private List<Long> snapshot() {
        return List.of(jdbc.queryForObject("select count(*) from patients",Long.class),
                jdbc.queryForObject("select count(*) from medical_records",Long.class),
                jdbc.queryForObject("select count(*) from patient_revisions",Long.class),
                jdbc.queryForObject("select count(*) from medical_record_revisions",Long.class),
                jdbc.queryForObject("select coalesce(sum(version),0) from patients",Long.class),
                jdbc.queryForObject("select coalesce(sum(version),0) from medical_records",Long.class));
    }
    private static Map<String,Object> profile() { return Map.of("fullName","Synthetic TCP Patient","phoneNumber","0900000001"); }
    private static Map<String,Object> medical() {
        return Map.of("doctorId",12,"visitDate","2026-10-20T02:00:00Z","symptoms","Synthetic symptoms","diagnosis","Synthetic diagnosis");
    }
    private static EmbeddedPostgres startPostgres() {
        try {
            Path root=Files.createTempDirectory(Path.of("target").toAbsolutePath(),"patient-identity-tcp-it-");
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

