package com.fclinic.authservice.security;

import com.fclinic.authservice.infrastructure.adapter.PatientOwnershipAdapter;
import com.fclinic.authservice.domain.exception.IdentityProviderException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PatientOwnershipAdapterTest {
    @Test void verifiesCreatorAndProfileOnServerAndDoesNotFollowRedirects() throws Exception {
        var subject = UUID.randomUUID(); var patient = UUID.randomUUID();
        var response = new AtomicReference<>("{\"data\":{\"id\":\"" + patient + "\",\"createdBy\":\"" + subject + "\",\"status\":\"ACTIVE\"}}");
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/me", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/me"); exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.start();
        try {
            var root = "http://127.0.0.1:" + server.getAddress().getPort();
            var adapter = new PatientOwnershipAdapter(URI.create(root + "/me"));
            assertThat(adapter.owns(subject, patient, "Bearer verified")).isTrue();
            assertThat(authorization.get()).isEqualTo("Bearer verified");
            assertThat(adapter.owns(UUID.randomUUID(), patient, "Bearer verified")).isFalse();
            assertThat(adapter.owns(subject, UUID.randomUUID(), "Bearer verified")).isFalse();
            assertThat(adapter.owns(subject, patient, null)).isFalse();
            response.set("{\"data\":{\"id\":\"" + patient + "\",\"createdBy\":\"" + subject + "\",\"status\":\"INACTIVE\"}}");
            assertThat(adapter.owns(subject, patient, "Bearer verified")).isFalse();
            authorization.set(null);
            assertThat(new PatientOwnershipAdapter(URI.create(root + "/redirect")).owns(subject, patient, "Bearer verified")).isFalse();
            assertThat(authorization.get()).isNull();
        } finally { server.stop(0); }
    }
    @Test void refusesNonLocalPlaintextEndpoints() {
        assertThatThrownBy(() -> new PatientOwnershipAdapter(URI.create("http://patient.example.test/me")))
                .isInstanceOf(IllegalStateException.class);
    }
}
