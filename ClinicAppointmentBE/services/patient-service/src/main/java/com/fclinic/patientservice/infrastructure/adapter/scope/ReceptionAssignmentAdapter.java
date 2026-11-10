package com.fclinic.patientservice.infrastructure.adapter.scope;

import com.fclinic.patientservice.application.port.out.ReceptionAssignmentPort;
import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.domain.model.PatientError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static com.fclinic.patientservice.domain.model.PatientError.Code.*;

@Component
@ConditionalOnProperty(name="patient.reception.enabled",havingValue="true")
public class ReceptionAssignmentAdapter implements ReceptionAssignmentPort {
    private static final String VERSION="patient-reception/1";
    private final URI uri;private final String credential;private final Clock clock;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
    public ReceptionAssignmentAdapter(@Value("${patient.reception.uri:https://localhost:8085/api/v1/auth/internal/reception-assignments}") URI uri,
            @Value("${patient.reception.client-secret:}") String secret,Clock clock) {
        if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || secret.isBlank())
            throw new IllegalArgumentException("Reception provider requires HTTPS and dedicated credentials");
        this.uri=uri;this.clock=clock;this.credential="Basic "+Base64.getEncoder().encodeToString(("patient-reception:"+secret).getBytes(StandardCharsets.UTF_8));
    }
    public boolean enabled(){return true;}
    public void assign(TrustedCaller c,Set<UUID> ids,String action) {
        if(ids.isEmpty())return;
        UUID nonce=UUID.randomUUID();
        try {
            Map<String,Object> caller=new LinkedHashMap<>();caller.put("subject",c.subject());caller.put("principalType",c.principalType().name());
            caller.put("patientId",c.patientId());caller.put("doctorId",c.doctorId());caller.put("serviceName",c.serviceName());
            String body=json.writeValueAsString(Map.of("contractVersion",VERSION,"nonce",nonce,"requestedAt",clock.instant(),"caller",caller,"patientIds",ids,"action",action));
            var response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).header("Authorization",credential)
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==403)throw new PatientError(FORBIDDEN);
            if(response.statusCode()!=200 || response.body().length()>65536
                    || !VERSION.equals(response.headers().firstValue("X-FClinic-Reception-Contract").orElse("")))throw new PatientError(DEPENDENCY_UNAVAILABLE);
            var data=json.readTree(response.body()).path("data");Set<UUID> received=new HashSet<>();
            for(var id:data.path("patientIds"))if(!received.add(UUID.fromString(id.asText())))throw new PatientError(DEPENDENCY_UNAVAILABLE);
            if(!data.path("contractVersion").asText().equals(VERSION) || !data.path("nonce").asText().equals(nonce.toString()) || !received.equals(ids))throw new PatientError(DEPENDENCY_UNAVAILABLE);
        }catch(PatientError e){throw e;}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new PatientError(DEPENDENCY_UNAVAILABLE);}
        catch(Exception e){throw new PatientError(DEPENDENCY_UNAVAILABLE);}
    }
}
