import com.fclinic.doctorservice.DoctorServiceApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.FileSystemResource;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import tools.jackson.databind.json.JsonMapper;

/** Real HTTP + temporary PostgreSQL; never connects to the deployment database. */
class ReviewDoctorLogic {
  static final JsonMapper JSON = JsonMapper.builder().build();
  static void check(boolean condition, String name) {
    if (!condition) throw new AssertionError("Not reproduced: " + name);
    System.out.println("REPRODUCED " + name);
  }
  static HttpResponse<String> post(HttpClient http, String url, String body) throws Exception {
    return http.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
  }
  public static void main(String[] args) throws Exception {
    Path root = Files.createTempDirectory("fclinic-review-doctor-");
    try (var pg = EmbeddedPostgres.builder().setPort(0).setDataDirectory(root.resolve("data"))
        .setOverrideWorkingDirectory(root.resolve("binaries").toFile()).setCleanDataDirectory(true)
        .setServerConfig("listen_addresses", "127.0.0.1").start()) {
      new ResourceDatabasePopulator(new FileSystemResource(
          "ClinicAppointmentBE/services/doctor-service/src/main/resources/db/schema/V001__doctor_uuid_empty.sql"))
          .execute(pg.getPostgresDatabase());
      try (var context = new SpringApplicationBuilder(DoctorServiceApplication.class).run(
          "--server.port=0", "--server.address=127.0.0.1", "--eureka.client.enabled=false",
          "--spring.cloud.discovery.enabled=false", "--spring.config.import=",
          "--spring.datasource.url=" + pg.getJdbcUrl("postgres", "postgres"),
          "--spring.datasource.username=postgres", "--spring.datasource.password=postgres",
          "--spring.jpa.hibernate.ddl-auto=validate"); var http = HttpClient.newHttpClient()) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        String base = "http://127.0.0.1:" + port + "/api/v1/doctors";
        var doctor = post(http, base, """
            {"name":"Audit Synthetic","specialty":"General","department":"General","consultationFee":100}
            """);
        check(doctor.statusCode() == 201, "anonymous_doctor_create_http_201");
        String id = JSON.readTree(doctor.body()).path("data").path("id").asText();
        String slot = """
            {"slotDate":"2030-01-01","startTime":"09:00:00","endTime":"10:00:00"}
            """;
        var first = post(http, base + "/" + id + "/slots", slot);
        check(first.statusCode() == 201, "anonymous_slot_create_http_201");
        var second = post(http, base + "/" + id + "/slots", slot);
        check(second.statusCode() == 201
            && !JSON.readTree(first.body()).path("data").path("id").asText()
                .equals(JSON.readTree(second.body()).path("data").path("id").asText()),
            "duplicate_doctor_same_hour_two_distinct_rows_http_201");
        var invalid = post(http, base, """
            {"name":"Invalid Synthetic","specialty":"General","department":"General","consultationFee":-100,"experienceYears":-2}
            """);
        check(invalid.statusCode() == 201, "negative_fee_and_experience_accepted_http_201");
      }
    }
  }
}
