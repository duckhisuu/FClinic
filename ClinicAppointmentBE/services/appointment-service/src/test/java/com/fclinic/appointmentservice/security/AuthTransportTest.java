package com.fclinic.appointmentservice.security;

import static org.assertj.core.api.Assertions.*;
import com.fclinic.appointmentservice.config.AppointmentSecuritySettings;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.infrastructure.client.auth.AuthIntrospectClient;
import com.fclinic.appointmentservice.infrastructure.client.config.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class AuthTransportTest {
  HttpServer server; FeignHarness harness; AuthIntrospectClient client; URI uri;
  final AtomicInteger requests = new AtomicInteger();
  @BeforeEach void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    server.start(); harness = new FeignHarness();
    uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect");
    var settings = new AppointmentSecuritySettings(uri, null, null, "client", "secret",
        null, null, null, null, null, null, null, null, null, null);
    client = harness.introspect(settings);
  }
  @AfterEach void close() { harness.close(); server.stop(0); }
  void respond(int status, String json) {
    server.createContext("/introspect", exchange -> {
      requests.incrementAndGet();
      assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo(FeignHeaderGuards.basic("client", "secret"));
      assertThat(exchange.getRequestHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
      assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).startsWith("application/x-www-form-urlencoded");
      assertThat(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("token=synthetic");
      send(exchange, status, json);
    });
  }
  void send(HttpExchange exchange, int status, String json) throws java.io.IOException {
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(status, bytes.length);
    try (var out = exchange.getResponseBody()) { out.write(bytes); }
  }
  Object exchange(long deadline) {
    return FeignCalls.call(() -> client.introspect(uri, "token=synthetic", FeignCalls.options(deadline)));
  }
  void unavailable() { assertThatThrownBy(() -> exchange(Long.MAX_VALUE))
      .isInstanceOfSatisfying(AppointmentError.class, e -> assertThat(e.status()).isEqualTo(503)); }
  @Test void exchangesOverHttpWithDedicatedClientCredentialAndFullUri() {
    respond(200, "{\"active\":false}");
    assertThat(client.introspect(uri, "token=synthetic", FeignCalls.options(Long.MAX_VALUE)).active()).isFalse();
    assertThat(requests.get()).isEqualTo(1);
  }
  @Test void upstreamCredentialFailureIs503AndPostIsNeverRetried() {
    for (int status : new int[]{401, 403, 503}) {
      respond(status, "{}"); unavailable(); server.removeContext("/introspect");
    }
    assertThat(requests.get()).isEqualTo(3);
  }
  @Test void rejectsDuplicateJsonTrailingTokensWrongScalarTypesAndUnexpectedFields() {
    for (String body : new String[]{"{\"active\":false,\"active\":true}", "{\"active\":false} {}",
        "{\"active\":\"false\"}", "{\"active\":false,\"unexpected\":true}", "{}"}) {
      respond(200, body); unavailable(); server.removeContext("/introspect");
    }
  }
  @Test void rejectsUnboundedResponsesIncludingChunkedBodies() {
    respond(200, "{\"value\":\"" + "x".repeat(262145) + "\"}"); unavailable();
    server.removeContext("/introspect");
    server.createContext("/introspect", ex -> {
      ex.getResponseHeaders().set("Content-Type", "application/json"); ex.sendResponseHeaders(200, 0);
      try (var out = ex.getResponseBody()) { out.write((" ".repeat(262145) + "{\"active\":false}").getBytes(StandardCharsets.UTF_8)); }
    });
    unavailable();
  }
  @Test void rejectsNonLoopbackPlaintextAndExpiredBudgetBeforeSending() {
    uri = URI.create("http://external.invalid/introspect"); unavailable();
    uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect");
    assertThatThrownBy(() -> exchange(System.nanoTime() - 1)).isInstanceOf(AppointmentError.class);
    assertThat(requests.get()).isZero();
  }
  @Test void rejectsUserInfoQueryAndFragmentBeforeSending() {
    String origin = "http://127.0.0.1:" + server.getAddress().getPort();
    for (String value : new String[]{origin + "/introspect?secret=value", origin + "/introspect#fragment",
        origin.replace("http://", "http://user:password@") + "/introspect"}) {
      uri = URI.create(value); unavailable();
    }
    assertThat(requests.get()).isZero();
  }
  @Test void redirectsNeverForwardCredentials() {
    server.createContext("/introspect", ex -> {
      requests.incrementAndGet(); ex.getResponseHeaders().set("Location", uri.resolve("/target").toString());
      ex.sendResponseHeaders(307, -1); ex.close();
    });
    server.createContext("/target", ex -> { requests.incrementAndGet(); send(ex, 200, "{\"active\":false}"); });
    unavailable(); assertThat(requests.get()).isEqualTo(1);
  }
  @Test void rejectsNonJsonAndEncodedBodies() {
    server.createContext("/introspect", ex -> {
      ex.getResponseHeaders().set("Content-Type", "text/plain");
      byte[] bytes = "{\"active\":false}".getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(200, bytes.length); try (var out = ex.getResponseBody()) { out.write(bytes); }
    });
    unavailable(); server.removeContext("/introspect");
    server.createContext("/introspect", ex -> {
      ex.getResponseHeaders().set("Content-Encoding", "gzip"); send(ex, 200, "{\"active\":false}");
    });
    unavailable();
  }
  @Test void wholeBodyMustArriveWithinRemainingDeadline() {
    server.createContext("/introspect", ex -> {
      ex.getResponseHeaders().set("Content-Type", "application/json"); ex.sendResponseHeaders(200, 0);
      try (var out = ex.getResponseBody()) {
        out.write('{'); out.flush();
        try { Thread.sleep(600); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        out.write("\"active\":false}".getBytes(StandardCharsets.UTF_8));
      } catch (java.io.IOException ignored) { }
    });
    long started = System.nanoTime();
    assertThatThrownBy(() -> exchange(started + Duration.ofMillis(150).toNanos())).isInstanceOf(AppointmentError.class);
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(550));
  }
}
