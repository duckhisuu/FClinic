package com.fclinic.patientservice;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import static org.assertj.core.api.Assertions.*;

/** Actual packaged Patient JAR + fresh test-owned native PostgreSQL. No fake production identity. */
class PatientUuidJarIT {
    static Path javaExecutable(String osName,Path javaHome) {
        String executable=osName.toLowerCase(java.util.Locale.ROOT).startsWith("windows") ? "java.exe" : "java";
        return javaHome.resolve("bin").resolve(executable);
    }
    @Test void actualLocalJarValidatesUuidSchemaAndDeniesUnavailableAuthorityWithoutWrites() throws Exception {
        Path root=Files.createTempDirectory(Path.of("target").toAbsolutePath(),"patient-uuid-jar-");
        try(var pg=EmbeddedPostgres.builder().setPort(0).setDataDirectory(root.resolve("data"))
                .setOverrideWorkingDirectory(root.resolve("binaries").toFile()).setCleanDataDirectory(true)
                .setServerConfig("listen_addresses","127.0.0.1").setServerConfig("timezone","UTC")
                .setServerConfig("log_min_error_statement","panic").start()) {
            new ResourceDatabasePopulator(new ClassPathResource("db/schema/uuid/V001__patient_uuid_empty.sql")).execute(pg.getPostgresDatabase());
            Path log=root.resolve("patient-startup.log");
            var builder=new ProcessBuilder(javaExecutable(System.getProperty("os.name"),Path.of(System.getProperty("java.home"))).toString(),"-jar",
                    Path.of("target/patient-service-1.0.0-SNAPSHOT.jar").toAbsolutePath().toString(),
                    "--spring.profiles.active=local,doctor-reference,appointment-reference","--server.address=127.0.0.1","--server.port=0",
                    "--patient.identity.enabled=false","--patient.scope.enabled=false","--eureka.client.enabled=false",
                    "--spring.cloud.discovery.enabled=false").redirectErrorStream(true).redirectOutput(log.toFile());
            var env=builder.environment();
            env.put("PATIENT_UUID_DB_URL",pg.getJdbcUrl("postgres","postgres"));env.put("PATIENT_UUID_DB_USERNAME","postgres");env.put("PATIENT_UUID_DB_PASSWORD","postgres");
            env.put("PATIENT_DOCTOR_REFERENCE_URI","http://127.0.0.1:1/api/v1/doctors");
            env.put("PATIENT_APPOINTMENT_REFERENCE_URI","http://127.0.0.1:1/internal/v1/appointments");
            env.put("PATIENT_DOCTOR_REFERENCE_CLIENT_ID","jar-doctor-client");env.put("PATIENT_DOCTOR_REFERENCE_CLIENT_SECRET","synthetic-jar-doctor-secret");
            env.put("PATIENT_APPOINTMENT_REFERENCE_CLIENT_ID","jar-appointment-client");env.put("PATIENT_APPOINTMENT_REFERENCE_CLIENT_SECRET","synthetic-jar-appointment-secret");
            Process app=builder.start();
            try(var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build()) {
                long deadline=System.nanoTime()+Duration.ofSeconds(60).toNanos();int port=0;boolean ready=false;int lastStatus=0;
                while(System.nanoTime()<deadline && app.isAlive()) {
                    var match=Pattern.compile("Tomcat started on port (\\d+) ").matcher(Files.readString(log));
                    if(match.find()) port=Integer.parseInt(match.group(1));
                    if(port>0) try {
                        var health=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/actuator/health"))
                                .timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.ofString());
                        lastStatus=health.statusCode();ready=lastStatus==200 && health.body().contains("UP");if(ready) break;
                    } catch(java.io.IOException ignored) { }
                    Thread.sleep(100);
                }
                assertThat(ready).as("bounded readiness: alive=%s lastStatus=%s; private log in test-owned target",app.isAlive(),lastStatus).isTrue();
                var uri=URI.create("http://127.0.0.1:"+port+"/api/v3/patients");
                var anonymous=HttpRequest.newBuilder(uri).header("Content-Type","application/json").header("X-User-Role","ROLE_STAFF")
                        .POST(HttpRequest.BodyPublishers.ofString("{}" )).build();
                assertThat(http.send(anonymous,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
                var unavailable=HttpRequest.newBuilder(uri).header("Content-Type","application/json").header("Authorization","Bearer synthetic-no-authority")
                        .POST(HttpRequest.BodyPublishers.ofString("{}" )).build();
                var denied=http.send(unavailable,HttpResponse.BodyHandlers.ofString());assertThat(denied.statusCode()).isEqualTo(503);
                assertThat(denied.body()).contains("DEPENDENCY_UNAVAILABLE").doesNotContain("secret","postgres");
                var jdbc=new JdbcTemplate(pg.getPostgresDatabase());
                for(String table:java.util.List.of("patients","medical_records","patient_revisions","medical_record_revisions"))
                    assertThat(jdbc.queryForObject("select count(*) from "+table,Long.class)).isZero();
            } finally {
                app.destroy();if(!app.waitFor(10,TimeUnit.SECONDS)) {app.destroyForcibly();assertThat(app.waitFor(5,TimeUnit.SECONDS)).isTrue();}
            }
        }
    }
}
