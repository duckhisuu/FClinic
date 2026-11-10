package com.fclinic.authservice.infrastructure.adapter;

import com.fclinic.authservice.application.port.out.PatientProvisioningPort;
import com.fclinic.authservice.application.port.out.PatientOnboardingPort.Reservation;
import com.fclinic.authservice.domain.exception.PatientOnboardingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Component
public class HttpPatientProvisioningAdapter implements PatientProvisioningPort {
    private final String uri, clientId, secret;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json = JsonMapper.builder().build();
    public HttpPatientProvisioningAdapter(
        @Value("${app.patient-onboarding.uri:http://localhost:8081/internal/v1/patient-onboarding}") String uri,
        @Value("${app.patient-onboarding.client-id:auth-patient-onboarding}") String clientId,
        @Value("${app.patient-onboarding.client-secret:}") String secret) {
        this.uri=uri; this.clientId=clientId; this.secret=secret;
    }
    public void ensureProfile(Reservation r) {
        try {
            URI target=URI.create(uri);
            boolean local=Set.of("localhost","127.0.0.1","[::1]").contains(target.getHost());
            if (secret.isBlank() || clientId.isBlank() || target.getUserInfo()!=null || target.getQuery()!=null
                    || target.getFragment()!=null || !("https".equals(target.getScheme()) || (local && "http".equals(target.getScheme()))))
                throw unavailable();
            var body = new LinkedHashMap<String,Object>();
            body.put("accountId",r.userId()); body.put("patientId",r.patientId()); body.put("subject",r.subject().toString());
            body.put("requestHash",r.requestHash()); body.put("profile",json.readTree(r.requestBody()));
            String basic=Base64.getEncoder().encodeToString((clientId+":"+secret).getBytes(StandardCharsets.UTF_8));
            var request=HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(10))
                .header("Authorization","Basic "+basic).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()==409) throw new PatientOnboardingException(409,"Hồ sơ không còn hoạt động hoặc tiến trình liên kết đang xung đột.");
            if (response.statusCode()!=200 || response.body().length()>16384) throw unavailable();
            var envelope=json.readTree(response.body()); var proof=envelope.path("data");
            if (envelope.path("status").asInt()!=200 || !r.userId().toString().equals(proof.path("accountId").asText())
                    || !r.patientId().toString().equals(proof.path("patientId").asText())
                    || !r.subject().toString().equals(proof.path("subject").asText())
                    || !r.requestHash().equals(proof.path("requestHash").asText())
                    || !"ACTIVE".equals(proof.path("status").asText())) throw unavailable();
        } catch (PatientOnboardingException ex) { throw ex; }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (Exception ex) { throw unavailable(); }
    }
    private PatientOnboardingException unavailable() {
        return new PatientOnboardingException(503,"Chưa hoàn tất khởi tạo hồ sơ. Vui lòng thử lại; tiến trình đã được giữ nguyên.");
    }
}
