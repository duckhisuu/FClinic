package com.fclinic.doctorservice;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "server.address=127.0.0.1","eureka.client.enabled=false","spring.cloud.discovery.enabled=false","spring.jpa.hibernate.ddl-auto=validate"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class DoctorBookingValidationHttpTest {
    static final EmbeddedPostgres PG=postgres(); static final HttpClient HTTP=HttpClient.newHttpClient();
    static final JsonMapper JSON=JsonMapper.builder().build();
    static EmbeddedPostgres postgres() {
        try {
            Path root=Files.createTempDirectory("doctor-booking-test-");
            var pg=EmbeddedPostgres.builder().setPort(0).setDataDirectory(root.resolve("data"))
                .setOverrideWorkingDirectory(root.resolve("bin").toFile()).setCleanDataDirectory(true).start();
            new ResourceDatabasePopulator(new ClassPathResource("db/schema/V001__doctor_uuid_empty.sql")).execute(pg.getPostgresDatabase());
            try(var connection=pg.getPostgresDatabase().getConnection();var statement=connection.createStatement();
                var sql=new ClassPathResource("db/schema/V002__doctor_booking_constraints.sql").getInputStream()) {
                statement.execute(new String(sql.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            }
            return pg;
        } catch(Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->PG.getJdbcUrl("postgres","postgres"));
        r.add("spring.datasource.username",()->"postgres");r.add("spring.datasource.password",()->"postgres");
    }
    @LocalServerPort int port;
    @AfterAll static void close() throws Exception {HTTP.close();PG.close();}
    HttpResponse<String> post(String path,String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/doctors"+path))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void negativeFeeAndExperienceAreRejectedAtHttpBoundary() throws Exception {
        assertThat(post("","{\"name\":\"Synthetic\",\"specialty\":\"General\",\"department\":\"General\",\"consultationFee\":-1}").statusCode()).isEqualTo(400);
        assertThat(post("","{\"name\":\"Synthetic\",\"specialty\":\"General\",\"department\":\"General\",\"consultationFee\":100,\"experienceYears\":-1}").statusCode()).isEqualTo(400);
    }
    @Test void concurrentDuplicateOffersCommitOneSlotAndReturnConflictForTheOther() throws Exception {
        var response=post("","{\"name\":\"Synthetic\",\"specialty\":\"General\",\"department\":\"General\",\"consultationFee\":100}");
        assertThat(response.statusCode()).isEqualTo(201);
        String path="/"+JSON.readTree(response.body()).path("data").path("id").asText()+"/slots";
        String body="{\"slotDate\":\"2030-01-01\",\"startTime\":\"09:00:00\",\"endTime\":\"10:00:00\"}";
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(()->post(path,body));var second=executor.submit(()->post(path,body));
            assertThat(java.util.List.of(first.get(10,TimeUnit.SECONDS).statusCode(),second.get(10,TimeUnit.SECONDS).statusCode()))
                .containsExactlyInAnyOrder(201,409);
        }
    }
}
