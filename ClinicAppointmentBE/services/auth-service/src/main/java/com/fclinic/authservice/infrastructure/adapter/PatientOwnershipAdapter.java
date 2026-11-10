package com.fclinic.authservice.infrastructure.adapter;

import com.fclinic.authservice.application.port.out.PatientOwnershipPort;
import com.fclinic.authservice.domain.exception.IdentityProviderException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Verify ownership with Patient, never trust a browser-supplied profile ID alone. */
@Component
public class PatientOwnershipAdapter implements PatientOwnershipPort {
    private final RestClient client;
    private final URI endpoint;

    public PatientOwnershipAdapter(@Value("${AUTH_PATIENT_OWN_PROFILE_URI:http://localhost:8081/api/v3/patients/me}") URI endpoint) {
        boolean loopback = "http".equalsIgnoreCase(endpoint.getScheme())
                && endpoint.getHost() != null
                && Set.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost());
        if (!("https".equalsIgnoreCase(endpoint.getScheme()) || loopback) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null) {
            throw new IllegalStateException("Patient ownership endpoint requires HTTPS or loopback HTTP");
        }
        this.endpoint = endpoint;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean owns(UUID subject, UUID patientId, String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return false;
        try {
            var response = client.get().uri(endpoint).header("Authorization", authorization).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response == null || !(response.get("data") instanceof Map<?, ?> profile)) return false;
            return patientId.toString().equals(profile.get("id"))
                    && subject.toString().equals(profile.get("createdBy"))
                    && "ACTIVE".equals(profile.get("status"));
        } catch (RestClientResponseException ex) {
            if (Set.of(401, 403, 404).contains(ex.getStatusCode().value())) return false;
            throw new IdentityProviderException("Không thể xác minh quyền sở hữu hồ sơ bệnh nhân");
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new IdentityProviderException("Không thể xác minh quyền sở hữu hồ sơ bệnh nhân");
        }
    }
}
