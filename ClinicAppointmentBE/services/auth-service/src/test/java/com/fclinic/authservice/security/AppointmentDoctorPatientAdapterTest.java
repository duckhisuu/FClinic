package com.fclinic.authservice.security;

import com.fclinic.authservice.infrastructure.adapter.AppointmentDoctorPatientAdapter;
import com.fclinic.authservice.domain.exception.IdentityProviderException;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AppointmentDoctorPatientAdapterTest {
    @Test void bindsResponseToRequestedDoctorAndRejectsMalformedOrUnavailableAssignments() throws Exception {
        UUID doctor = UUID.randomUUID(), patient = UUID.randomUUID();
        var response = new AtomicReference<>("{\"status\":200,\"message\":\"OK\",\"data\":{\"doctorId\":\"" + doctor + "\",\"patientIds\":[\"" + patient + "\"]}}");
        var authorization = new AtomicReference<String>();
        var path = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/assignments", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization")); path.set(exchange.getRequestURI().getPath());
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        var adapter = new AppointmentDoctorPatientAdapter(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/assignments"), "assignments", "dedicated-secret");
        try {
            assertThat(adapter.assignedPatients(doctor)).containsExactly(patient);
            assertThat(path.get()).isEqualTo("/assignments/" + doctor);
            assertThat(authorization.get()).isEqualTo("Basic " + Base64.getEncoder().encodeToString("assignments:dedicated-secret".getBytes(StandardCharsets.UTF_8)));
            String valid = response.get();
            response.set(valid.replace(doctor.toString(), UUID.randomUUID().toString()));
            assertThatThrownBy(() -> adapter.assignedPatients(doctor)).isInstanceOf(IdentityProviderException.class);
            response.set(valid.replace("[\"" + patient + "\"]", "[\"" + patient + "\",\"" + patient + "\"]"));
            assertThatThrownBy(() -> adapter.assignedPatients(doctor)).isInstanceOf(IdentityProviderException.class);
            response.set(valid.replace(patient.toString(), "invalid-id"));
            assertThatThrownBy(() -> adapter.assignedPatients(doctor)).isInstanceOf(IdentityProviderException.class);
            response.set("{}");
            assertThatThrownBy(() -> adapter.assignedPatients(doctor)).isInstanceOf(IdentityProviderException.class);
        } finally { server.stop(0); }
        assertThatThrownBy(() -> adapter.assignedPatients(doctor)).isInstanceOf(IdentityProviderException.class);
    }
}
