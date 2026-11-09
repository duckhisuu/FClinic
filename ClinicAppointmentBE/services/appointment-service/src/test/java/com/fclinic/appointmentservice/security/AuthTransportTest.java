package com.fclinic.appointmentservice.security;

import static org.assertj.core.api.Assertions.*;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.infrastructure.security.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class AuthTransportTest {
  HttpServer server; AuthHttpTransport transport; URI uri;
  final AtomicInteger requests = new AtomicInteger();
  @BeforeEach void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start(); transport = new AuthHttpTransport(); uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect");
  }
  @AfterEach void close() { transport.close(); server.stop(0); }
  void respond(int status, String json) {
    server.createContext("/introspect", exchange -> {
      requests.incrementAndGet();
      assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo(AuthHttpTransport.basic("client", "secret"));
      assertThat(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("token=synthetic");
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(status, bytes.length);
      try(var out = exchange.getResponseBody()) { out.write(bytes); }
    });
  }
  void unavailable() { assertThatThrownBy(() -> transport.post(uri, "client", "secret", "application/x-www-form-urlencoded", "token=synthetic", Long.MAX_VALUE))
      .isInstanceOfSatisfying(AppointmentError.class, e -> assertThat(e.status()).isEqualTo(503)); }
  @Test void exchangesOverHttpWithDedicatedClientCredential() {
    respond(200, "{\"active\":false}");
    assertThat(transport.post(uri, "client", "secret", "application/x-www-form-urlencoded", "token=synthetic", Long.MAX_VALUE).path("active").asBoolean()).isFalse();
    assertThat(requests.get()).isEqualTo(1);
  }
  @Test void upstreamCredentialFailureIs503() { respond(401, "{}"); unavailable(); }
  @Test void rejectsDuplicateJsonAndUnboundedResponses() {
    respond(200, "{\"active\":false,\"active\":true}"); unavailable();
    server.removeContext("/introspect"); respond(200, "{\"value\":\"" + "x".repeat(262145) + "\"}"); unavailable();
  }
  @Test void rejectsNonLoopbackPlaintextAndExpiredBudgetBeforeSending() {
    uri = URI.create("http://external.invalid/introspect"); unavailable(); assertThat(requests.get()).isZero();
  }
}
