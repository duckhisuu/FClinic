package com.fclinic.patientservice;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "server.address=127.0.0.1","eureka.client.enabled=false","spring.cloud.discovery.enabled=false",
    "patient.identity.enabled=false","patient.scope.enabled=false",
    "patient.onboarding.client-id=auth-onboarding-test","patient.onboarding.client-secret=synthetic-onboarding-secret"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PatientOnboardingHttpTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final EmbeddedPostgres PG=postgres();
    static final HttpClient HTTP=HttpClient.newHttpClient();
    static EmbeddedPostgres postgres() {
        try {
            Path root=Files.createTempDirectory("patient-onboarding-http-");
            var pg=EmbeddedPostgres.builder().setPort(0).setDataDirectory(root.resolve("data"))
                .setOverrideWorkingDirectory(root.resolve("bin").toFile()).setCleanDataDirectory(true).start();
            new ResourceDatabasePopulator(new ClassPathResource("db/schema/uuid/V001__patient_uuid_empty.sql"),
                new ClassPathResource("db/schema/uuid/V002__patient_onboarding.sql")).execute(pg.getPostgresDatabase());
            return pg;
        } catch(Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->PG.getJdbcUrl("postgres","postgres"));
        r.add("spring.datasource.username",()->"postgres");r.add("spring.datasource.password",()->"postgres");
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @AfterAll static void close() throws Exception {HTTP.close();PG.close();}
    @BeforeEach void clear() {
        jdbc.update("DELETE FROM patient_onboardings");jdbc.update("DELETE FROM patient_revisions");jdbc.update("DELETE FROM patients");
    }
    Map<String,Object> body() throws Exception {
        var profile=new TreeMap<String,Object>();
        profile.put("fullName","Synthetic");profile.put("phoneNumber","+84900000001");profile.put("email","test@example.test");
        profile.put("dateOfBirth","1995-01-01");profile.put("gender","OTHER");profile.put("address",null);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsString(profile).getBytes(StandardCharsets.UTF_8)));
        return new LinkedHashMap<>(Map.of("accountId",UUID.randomUUID().toString(),"patientId",UUID.randomUUID().toString(),
            "subject",UUID.randomUUID().toString(),"requestHash",hash,"profile",profile));
    }
    HttpResponse<String> post(Map<String,Object> body,String auth) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/internal/v1/patient-onboarding"))
            .header("Content-Type","application/json");
        if(auth!=null)request.header("Authorization",auth);
        return HTTP.send(request.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    String basic() { return "Basic "+Base64.getEncoder().encodeToString("auth-onboarding-test:synthetic-onboarding-secret".getBytes(StandardCharsets.UTF_8)); }
    @Test void credentialAndHashAreRequiredBeforeAnyPatientWrite() throws Exception {
        var body=body(); assertThat(post(body,null).statusCode()).isEqualTo(401);
        assertThat(post(body,"Bearer arbitrary").statusCode()).isEqualTo(401);
        body.put("requestHash","0".repeat(64)); assertThat(post(body,basic()).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patients",Integer.class)).isZero();
    }
    @Test void concurrentRequestsAndReplayPersistExactlyOneOwnedProfileAndRevision() throws Exception {
        var body=body();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(()->post(body,basic()));var second=executor.submit(()->post(body,basic()));
            assertThat(first.get(10,TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThat(second.get(10,TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
        assertThat(post(body,basic()).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patients",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient_revisions",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT created_by FROM patients",String.class)).isEqualTo(body.get("subject"));
        body.put("subject",UUID.randomUUID().toString());assertThat(post(body,basic()).statusCode()).isEqualTo(422);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patients",Integer.class)).isEqualTo(1);
    }
    @Test void cannotClaimAnExistingUnrelatedProfileOrReactivateAnInactiveOne() throws Exception {
        var body=body();assertThat(post(body,basic()).statusCode()).isEqualTo(200);
        var other=body();other.put("patientId",body.get("patientId"));assertThat(post(other,basic()).statusCode()).isEqualTo(422);
        jdbc.update("UPDATE patients SET status='INACTIVE'");assertThat(post(body,basic()).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patients",Integer.class)).isEqualTo(1);
    }
}
