package com.fclinic.apigateway;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"eureka.client.enabled=false", "server.address=127.0.0.1"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class GatewayAppointmentRouteTest {
  static final AtomicReference<String> AUTHORIZATION = new AtomicReference<>();
  static final HttpServer BACKEND = backend();
  @LocalServerPort int port;
  @DynamicPropertySource static void configure(DynamicPropertyRegistry r) {
    r.add("spring.cloud.discovery.client.simple.instances.appointment-service[0].uri", () -> "http://127.0.0.1:" + BACKEND.getAddress().getPort());
  }
  @AfterAll static void stop() { BACKEND.stop(0); }
  @Test void v2UsesAppointmentRouteAndForwardsOriginalBearer() throws Exception {
    try (var http = HttpClient.newHttpClient()) {
      var uri = URI.create("http://127.0.0.1:" + port + "/api/v2/appointments");
      var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer original-user").build(), HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200); assertThat(response.body()).isEqualTo("/api/v2/appointments");
      assertThat(AUTHORIZATION.get()).isEqualTo("Bearer original-user");
      var anonymous = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString());
      assertThat(anonymous.statusCode()).isEqualTo(401);
    }
  }
  static HttpServer backend() {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
        String credential = exchange.getRequestHeaders().getFirst("Authorization"); AUTHORIZATION.set(credential);
        byte[] bytes = exchange.getRequestURI().getPath().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(credential == null ? 401 : 200, bytes.length);
        try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
      }); server.start(); return server;
    } catch (Exception e) { throw new ExceptionInInitializerError(e); }
  }
}
