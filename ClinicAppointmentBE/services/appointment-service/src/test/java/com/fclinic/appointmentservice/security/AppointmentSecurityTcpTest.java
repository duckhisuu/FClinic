package com.fclinic.appointmentservice.security;

import static com.fclinic.appointmentservice.security.SecurityFixtures.*;
import static org.assertj.core.api.Assertions.*;
import com.fclinic.appointmentservice.infrastructure.security.AuthHttpTransport;
import com.sun.net.httpserver.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import tools.jackson.databind.JsonNode;

/** Actual Appointment TCP, Flyway and isolated PostgreSQL; upstream identity/reference servers are doubles. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false", "server.address=127.0.0.1",
    "appointment.outbox-enabled=false", "appointment.reminder-lead=", "appointment.security.patient-token-uri="})
@Import(AppointmentSecurityTcpTest.FixedClock.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AppointmentSecurityTcpTest {
  static final EmbeddedPostgres PG = postgres();
  static final AtomicBoolean STAFF_ALLOWED = new AtomicBoolean(true), AUTH_DOWN = new AtomicBoolean(false);
  static final AtomicInteger PATIENT_READS = new AtomicInteger();
  static final UUID SLOT = UUID.randomUUID();
  static final UUID NEXT_SLOT = UUID.randomUUID();
  static final HttpServer UPSTREAM = upstream();
  static final HttpClient HTTP = HttpClient.newHttpClient();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @TestConfiguration static class FixedClock { @Bean @Primary Clock securityClock() { return CLOCK; } }
  static String upstreamUrl() { return "http://127.0.0.1:" + UPSTREAM.getAddress().getPort(); }
  @DynamicPropertySource static void configure(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
    r.add("spring.datasource.username", () -> "postgres"); r.add("spring.datasource.password", () -> "postgres");
    r.add("appointment.security.introspection-uri", () -> upstreamUrl() + "/introspect");
    r.add("appointment.security.scope-uri", () -> upstreamUrl() + "/scope");
    r.add("appointment.security.issuer", () -> "https://identity.example.test");
    r.add("appointment.security.client-id", () -> "identity-client"); r.add("appointment.security.client-secret", () -> "identity-secret");
    r.add("appointment.security.scope-client-id", () -> "scope-client"); r.add("appointment.security.scope-client-secret", () -> "scope-secret");
    r.add("appointment.security.patient-client-id", () -> "patient-reference"); r.add("appointment.security.patient-client-secret", () -> "patient-secret");
    r.add("appointment.security.notification-client-secret", () -> "notification-secret");
    r.add("appointment.security.patient-token-client-secret", () -> "token-secret");
    r.add("appointment.patient-url", AppointmentSecurityTcpTest::upstreamUrl);
    r.add("appointment.doctor-url", AppointmentSecurityTcpTest::upstreamUrl); r.add("appointment.service-token", () -> "machine-token");
    r.add("appointment.providers.doctor-client-id", () -> "doctor-reference");
    r.add("appointment.providers.doctor-client-secret", () -> "doctor-secret");
    r.add("AUTH_APPOINTMENT_ASSIGNMENTS_CLIENT_ID", () -> "auth-assignments");
    r.add("AUTH_APPOINTMENT_ASSIGNMENTS_CLIENT_SECRET", () -> "assignment-secret");
  }
  @BeforeEach void reset() { jdbc.execute("TRUNCATE appointments CASCADE"); STAFF_ALLOWED.set(true); AUTH_DOWN.set(false); PATIENT_READS.set(0); }
  @AfterAll static void close() throws Exception { HTTP.close(); UPSTREAM.stop(0); PG.close(); }
  HttpResponse<String> request(String method, String path, String token, Object body, UUID key) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v2/appointments" + path)).timeout(Duration.ofSeconds(10));
    if (token != null) builder.header("Authorization", token.startsWith("Basic ") ? token : "Bearer " + token);
    if (key != null) builder.header("Idempotency-Key", key.toString());
    builder.header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody()
        : HttpRequest.BodyPublishers.ofString(AuthHttpTransport.JSON.writeValueAsString(body)));
    return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
  JsonNode data(HttpResponse<String> response) { return AuthHttpTransport.JSON.readTree(response.body()).path("data"); }
  HttpResponse<String> book(String token, UUID patient) throws Exception {
    return book(token, patient, SLOT);
  }
  HttpResponse<String> book(String token, UUID patient, UUID slot) throws Exception {
    return request("POST", "", token, Map.of("patientId", patient, "doctorId", DOCTOR, "slotId", slot), UUID.randomUUID());
  }
  @Test void anonymousWrongOwnerAndInactiveTokensCannotCreate() throws Exception {
    assertThat(book(null, PATIENT).statusCode()).isEqualTo(401);
    assertThat(book("inactive", PATIENT).statusCode()).isEqualTo(401);
    assertThat(book("bob", PATIENT).statusCode()).isEqualTo(403);
    assertThat(PATIENT_READS.get()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments", Integer.class)).isZero();
  }
  HttpResponse<String> assignedPatients(UUID doctor, String authorization) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/v1/doctor-patient-assignments/" + doctor));
    if (authorization != null) request.header("Authorization", authorization);
    return HTTP.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
  }
  @Test void clinicalAssignmentsFollowCommittedBookingCancellationAndDoctorBoundaries() throws Exception {
    String basic = "Basic " + Base64.getEncoder().encodeToString("auth-assignments:assignment-secret".getBytes(StandardCharsets.UTF_8));
    assertThat(assignedPatients(DOCTOR, null).statusCode()).isEqualTo(401);
    assertThat(assignedPatients(DOCTOR, "Bearer alice").statusCode()).isEqualTo(401);
    assertThat(data(assignedPatients(DOCTOR, basic)).path("patientIds").size()).isZero();
    var first = book("alice", PATIENT); assertThat(first.statusCode()).isEqualTo(201);
    var second = book("bob", OTHER, NEXT_SLOT); assertThat(second.statusCode()).isEqualTo(201);
    var assigned = assignedPatients(DOCTOR, basic);
    assertThat(assigned.statusCode()).isEqualTo(200);
    assertThat(assigned.headers().firstValue("Cache-Control")).contains("no-store");
    assertThat(data(assigned).path("patientIds").toString()).contains(PATIENT.toString(), OTHER.toString());
    assertThat(data(assignedPatients(UUID.randomUUID(), basic)).path("patientIds").size()).isZero();
    String id = data(first).path("id").asText();
    assertThat(request("PUT", "/" + id + "/cancel", "alice", Map.of("version", 0), UUID.randomUUID()).statusCode()).isEqualTo(200);
    assertThat(data(assignedPatients(DOCTOR, basic)).path("patientIds").toString()).doesNotContain(PATIENT.toString()).contains(OTHER.toString());
    jdbc.update("update appointments set status='NO_SHOW' where id=?", UUID.fromString(data(second).path("id").asText()));
    assertThat(data(assignedPatients(DOCTOR, basic)).path("patientIds").size()).isZero();
    jdbc.update("update appointments set status='COMPLETED' where id=?", UUID.fromString(data(second).path("id").asText()));
    assertThat(data(assignedPatients(DOCTOR, basic)).path("patientIds").toString()).contains(OTHER.toString());
  }
  @Test void anotherConfirmedBookingPreservesAssignmentAfterCancellation() throws Exception {
    String basic = "Basic " + Base64.getEncoder().encodeToString("auth-assignments:assignment-secret".getBytes(StandardCharsets.UTF_8));
    var first = book("alice", PATIENT); assertThat(first.statusCode()).isEqualTo(201);
    assertThat(book("alice", PATIENT, NEXT_SLOT).statusCode()).isEqualTo(201);
    assertThat(data(assignedPatients(DOCTOR, basic)).path("patientIds").size()).isEqualTo(1);
    assertThat(request("PUT", "/" + data(first).path("id").asText() + "/cancel", "alice", Map.of("version", 0), UUID.randomUUID()).statusCode()).isEqualTo(200);
    assertThat(data(assignedPatients(DOCTOR, basic)).path("patientIds").toString()).contains(PATIENT.toString());
  }
  @Test void selfBookingCancelAndDurableReplayWorkWithVerifiedAudit() throws Exception {
    var booked = book("alice", PATIENT); assertThat(booked.statusCode()).isEqualTo(201);
    String id = data(booked).path("id").asText(); UUID key = UUID.randomUUID();
    var body = Map.of("version", 0);
    var cancelled = request("PUT", "/" + id + "/cancel", "alice", body, key);
    assertThat(cancelled.statusCode()).isEqualTo(200);
    var replay = request("PUT", "/" + id + "/cancel", "alice", body, key);
    assertThat(replay.statusCode()).isEqualTo(200); assertThat(replay.body()).isEqualTo(cancelled.body());
    assertThat(jdbc.queryForList("SELECT changed_by FROM appointment_history", String.class)).containsOnly("PATIENT");
    assertThat(request("GET", "/" + id, "bob", null, null).statusCode()).isEqualTo(403);
    assertThat(request("GET", "/" + id + "/history", "bob", null, null).statusCode()).isEqualTo(403);
  }
  @Test void revokedStaffScopeBlocksReplayAndUnfilteredListsAreScoped() throws Exception {
    var booked = book("staff", PATIENT); assertThat(booked.statusCode()).isEqualTo(201);
    assertThat(book("bob", OTHER, NEXT_SLOT).statusCode()).isEqualTo(201);
    String id = data(booked).path("id").asText(); UUID key = UUID.randomUUID();
    var body = Map.of("version", 0);
    assertThat(request("PUT", "/" + id + "/cancel", "staff", body, key).statusCode()).isEqualTo(200);
    STAFF_ALLOWED.set(false);
    assertThat(request("PUT", "/" + id + "/cancel", "staff", body, key).statusCode()).isEqualTo(403);
    assertThat(request("GET", "", "staff", null, null).statusCode()).isEqualTo(403);
    STAFF_ALLOWED.set(true);
    assertThat(data(request("GET", "", "staff", null, null)).path("totalElements").intValue()).isEqualTo(1);
    assertThat(data(request("GET", "", "bob", null, null)).path("totalElements").intValue()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments", Integer.class)).isEqualTo(2);
  }
  @Test void ownerReschedulePersistsAndReplaysButAnotherPatientCannotChange() throws Exception {
    var booked = book("alice", PATIENT); assertThat(booked.statusCode()).isEqualTo(201);
    String id = data(booked).path("id").asText(); var body = Map.of("version", 0, "slotId", NEXT_SLOT); UUID key = UUID.randomUUID();
    assertThat(request("PUT", "/" + id + "/reschedule", "bob", body, key).statusCode()).isEqualTo(403);
    var changed = request("PUT", "/" + id + "/reschedule", "alice", body, key);
    assertThat(changed.statusCode()).isEqualTo(200); assertThat(data(changed).path("slotId").asText()).isEqualTo(NEXT_SLOT.toString());
    assertThat(request("PUT", "/" + id + "/reschedule", "alice", body, key).body()).isEqualTo(changed.body());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM appointment_history", Integer.class)).isEqualTo(2);
  }
  @Test void authenticatedMachineProjectionIsMinimalAndCannotCancel() throws Exception {
    var booked = book("alice", PATIENT); assertThat(booked.statusCode()).isEqualTo(201);
    String id = data(booked).path("id").asText();
    String basic = AuthHttpTransport.basic("patient-reference", "patient-secret");
    var reference = request("GET", "/" + id + "/medical-reference", basic, null, null);
    assertThat(reference.statusCode()).isEqualTo(200); assertThat(data(reference).size()).isEqualTo(4);
    assertThat(reference.headers().firstValue("X-FClinic-Reference-Contract")).contains("patient-appointment-medical-reference/2");
    assertThat(request("PUT", "/" + id + "/cancel", basic, Map.of("version", 0), UUID.randomUUID()).statusCode()).isEqualTo(401);
  }
  @Test void authOutageFails503WithoutWrite() throws Exception {
    AUTH_DOWN.set(true); assertThat(book("alice", PATIENT).statusCode()).isEqualTo(503);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments", Integer.class)).isZero();
  }
  static EmbeddedPostgres postgres() {
    try {
      Path root = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "appointment-security-");
      return EmbeddedPostgres.builder().setPort(0).setDataDirectory(root.resolve("data"))
          .setOverrideWorkingDirectory(root.resolve("binaries").toFile()).setCleanDataDirectory(true)
          .setServerConfig("listen_addresses", "127.0.0.1").setServerConfig("timezone", "UTC").start();
    } catch (Exception e) { throw new ExceptionInInitializerError(e); }
  }
  static HttpServer upstream() {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", AppointmentSecurityTcpTest::handle); server.start(); return server;
    } catch (Exception e) { throw new ExceptionInInitializerError(e); }
  }
  static void handle(HttpExchange ex) throws java.io.IOException {
    String path = ex.getRequestURI().getPath();
    if (path.equals("/introspect")) {
      if (AUTH_DOWN.get()) { respond(ex, 503, Map.of()); return; }
      if (!AuthHttpTransport.basic("identity-client", "identity-secret").equals(ex.getRequestHeaders().getFirst("Authorization"))) { respond(ex, 401, Map.of()); return; }
      String form = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      String token = URLDecoder.decode(form.split("&")[0].substring(6), StandardCharsets.UTF_8);
      if (!Set.of("alice", "bob", "staff").contains(token)) { respond(ex, 200, Map.of("active", false)); return; }
      Map<String,Object> wire = new LinkedHashMap<>();
      wire.put("active", true); wire.put("contractVersion", "patient-identity-introspection/2"); wire.put("iss", "https://identity.example.test");
      wire.put("aud", List.of("appointment-service")); wire.put("exp", CLOCK.instant().plusSeconds(600).getEpochSecond());
      wire.put("sub", "subject-" + token); wire.put("principalType", "USER"); wire.put("userId", UUID.randomUUID());
      wire.put("patientId", token.equals("staff") ? null : token.equals("alice") ? PATIENT : OTHER); wire.put("doctorId", null);
      wire.put("authorities", List.of(token.equals("staff") ? "ROLE_STAFF" : "ROLE_PATIENT")); respond(ex, 200, wire); return;
    }
    if (path.equals("/scope")) {
      if (!AuthHttpTransport.basic("scope-client", "scope-secret").equals(ex.getRequestHeaders().getFirst("Authorization"))) { respond(ex, 401, Map.of()); return; }
      var q = AuthHttpTransport.JSON.readTree(ex.getRequestBody().readAllBytes());
      Map<String,Object> result = new LinkedHashMap<>(); result.put("contractVersion", "appointment-work-scope/1"); result.put("request", q);
      result.put("evaluatedAt", CLOCK.instant()); result.put("allowed", STAFF_ALLOWED.get());
      result.put("patientIds", STAFF_ALLOWED.get() && q.path("operation").asText().equals("APPOINTMENT_SEARCH") ? List.of(PATIENT) : null);
      Map<String,Object> evidence = new LinkedHashMap<>(); evidence.put("assignmentId", UUID.randomUUID()); evidence.put("active", true);
      evidence.put("validFrom", CLOCK.instant().minusSeconds(60)); evidence.put("validUntil", CLOCK.instant().plusSeconds(60)); evidence.put("visitId", null);
      result.put("evidence", STAFF_ALLOWED.get() ? evidence : null); respond(ex, 200, result); return;
    }
    if (path.startsWith("/internal/v1/doctors/")) {
      ex.getResponseHeaders().set("X-FClinic-Reference-Contract", "appointment-doctor-reference/1");
      if (!AuthHttpTransport.basic("doctor-reference", "doctor-secret").equals(ex.getRequestHeaders().getFirst("Authorization"))) { respond(ex, 401, Map.of()); return; }
    } else if (!"Bearer machine-token".equals(ex.getRequestHeaders().getFirst("Authorization"))) { respond(ex, 401, Map.of()); return; }
    Map<String,Object> d = new LinkedHashMap<>();
    if (path.startsWith("/api/v3/patients/")) {
      UUID profileId = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
      PATIENT_READS.incrementAndGet(); d.put("id", profileId); d.put("patientCode", "PAT-" + profileId); d.put("fullName", "Synthetic"); d.put("phoneNumber", "+84900000001");
      for (String field : List.of("email", "dateOfBirth", "gender", "address", "createdAt", "updatedAt", "createdBy", "updatedBy")) d.put(field, null);
      d.put("status", "ACTIVE"); d.put("version", 0);
    } else if (path.equals("/internal/v1/doctors/" + DOCTOR)) {
      d.put("id", DOCTOR); d.put("name", "Doctor"); d.put("specialty", "General"); d.put("active", true); d.put("consultationFee", 10);
      for (String field : List.of("department", "qualification", "experienceYears", "roomNumber", "bio", "avatarUrl")) d.put(field, null);
    } else if (path.equals("/internal/v1/doctors/" + DOCTOR + "/slots/" + SLOT) || path.equals("/internal/v1/doctors/" + DOCTOR + "/slots/" + NEXT_SLOT)) {
      boolean next = path.endsWith(NEXT_SLOT.toString());
      d.put("id", next ? NEXT_SLOT : SLOT); d.put("doctorId", DOCTOR); d.put("slotDate", "2026-11-20"); d.put("startTime", next ? "10:00:00" : "09:00:00"); d.put("endTime", next ? "11:00:00" : "10:00:00"); d.put("published", true);
    } else { respond(ex, 404, Map.of()); return; }
    respond(ex, 200, Map.of("status", 200, "message", "Synthetic provider", "data", d));
  }
  static void respond(HttpExchange ex, int status, Object body) throws java.io.IOException {
    byte[] bytes = AuthHttpTransport.JSON.writeValueAsBytes(body); ex.getResponseHeaders().set("Content-Type", "application/json");
    ex.sendResponseHeaders(status, bytes.length); try (var stream = ex.getResponseBody()) { stream.write(bytes); }
  }
}
