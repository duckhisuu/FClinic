package com.fclinic.authservice.infrastructure.adapter;

import com.fclinic.authservice.application.port.out.PatientOwnershipPort;
import com.fclinic.authservice.domain.exception.IdentityProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;

@Component
public class PatientOwnershipAdapter implements PatientOwnershipPort {
    private final URI endpoint;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public PatientOwnershipAdapter(@Value("${auth.patient-service-url:http://localhost:8081}") String base) {
        URI uri = URI.create(base);
        boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost());
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme()))))
            throw new IllegalArgumentException("Patient ownership provider requires HTTPS or loopback HTTP");
        endpoint = uri.resolve("/api/v3/patients/self-profile");
    }

    @Override public UUID selfRegisteredPatient(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new AccessDeniedException("Bearer required");
        try {
            var response = client.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(8))
                    .header("Authorization", authorization).header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 404
                    || response.statusCode() == 409) throw new AccessDeniedException("No active self-owned Patient profile");
            if (response.statusCode() != 200) throw new IdentityProviderException("Patient ownership provider unavailable");
            var data = json.readTree(response.body()).path("data");
            if (!"ACTIVE".equals(data.path("status").asText())) throw new AccessDeniedException("Inactive Patient profile");
            UUID id = UUID.fromString(data.path("id").asText());
            if (id.version() != 4 || id.variant() != 2) throw new IllegalArgumentException("Invalid Patient UUID");
            return id;
        } catch (AccessDeniedException | IdentityProviderException ex) { throw ex; }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IdentityProviderException("Patient ownership provider interrupted");
        } catch (Exception ex) { throw new IdentityProviderException("Patient ownership could not be verified"); }
    }
}
