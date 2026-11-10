package com.fclinic.authservice.infrastructure.adapter;

import com.fclinic.authservice.application.port.out.DoctorPatientAssignmentPort;
import com.fclinic.authservice.domain.exception.IdentityProviderException;
import com.fclinic.authservice.domain.util.UuidV4;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AppointmentDoctorPatientAdapter implements DoctorPatientAssignmentPort {
    public record AssignmentResponse(int status, String message, AssignmentData data) { }
    public record AssignmentData(UUID doctorId, List<UUID> patientIds) { }
    private final RestClient client;
    private final URI endpoint;
    private final String clientId, secret;

    public AppointmentDoctorPatientAdapter(
            @Value("${AUTH_APPOINTMENT_ASSIGNMENTS_URI:http://localhost:8083/internal/v1/doctor-patient-assignments}") URI endpoint,
            @Value("${AUTH_APPOINTMENT_ASSIGNMENTS_CLIENT_ID:auth-doctor-patient-assignments}") String clientId,
            @Value("${AUTH_APPOINTMENT_ASSIGNMENTS_CLIENT_SECRET:}") String secret) {
        boolean loopback = "http".equalsIgnoreCase(endpoint.getScheme()) && endpoint.getHost() != null
                && Set.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost());
        if (!("https".equalsIgnoreCase(endpoint.getScheme()) || loopback) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null)
            throw new IllegalStateException("Appointment assignments require HTTPS or loopback HTTP");
        this.endpoint = endpoint; this.clientId = clientId; this.secret = secret;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(2));
        client = RestClient.builder().requestFactory(factory).build();
    }
    @Override
    public Set<UUID> assignedPatients(UUID doctorId) {
        if (!UuidV4.isUuidV4(doctorId) || clientId.isBlank() || secret.isBlank()) throw unavailable();
        try {
            var response = client.get().uri(URI.create(endpoint.toString() + "/" + doctorId))
                    .headers(headers -> headers.setBasicAuth(URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                            URLEncoder.encode(secret, StandardCharsets.UTF_8))).retrieve().body(AssignmentResponse.class);
            if (response == null || response.status() != 200 || response.data() == null
                    || !doctorId.equals(response.data().doctorId()) || response.data().patientIds() == null
                    || response.data().patientIds().size() > 1000
                    || response.data().patientIds().stream().anyMatch(id -> !UuidV4.isUuidV4(id))) throw unavailable();
            var ids = Set.copyOf(response.data().patientIds());
            if (ids.size() != response.data().patientIds().size()) throw unavailable();
            return ids;
        } catch (org.springframework.web.client.RestClientException | IllegalArgumentException ex) { throw unavailable(); }
    }
    private static IdentityProviderException unavailable() {
        return new IdentityProviderException("Chưa thể kiểm tra phân công bác sĩ từ lịch hẹn. Vui lòng thử lại.");
    }
}
