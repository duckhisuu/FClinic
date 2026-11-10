package com.fclinic.authservice.security;

import com.fclinic.authservice.application.port.out.PatientOnboardingPort.Reservation;
import com.fclinic.authservice.infrastructure.adapter.HttpPatientProvisioningAdapter;
import com.fclinic.authservice.domain.exception.PatientOnboardingException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class HttpPatientProvisioningAdapterTest {
    @Test void onlyAcceptsMatchingActiveProofAndSendsDedicatedCredential() throws Exception {
        var json=JsonMapper.builder().build();var wrong=new AtomicBoolean(false);
        var r=new Reservation(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"{}","{}","a".repeat(64));
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/provision",ex->{
            var input=json.readTree(ex.getRequestBody());
            String expected="Basic "+Base64.getEncoder().encodeToString("onboarding:synthetic-secret".getBytes(StandardCharsets.UTF_8));
            if(!expected.equals(ex.getRequestHeaders().getFirst("Authorization"))) {ex.sendResponseHeaders(401,-1);ex.close();return;}
            byte[] body=json.writeValueAsBytes(Map.of("status",200,"message","OK","data",Map.of(
                "accountId",input.path("accountId").asText(),"patientId",wrong.get()?UUID.randomUUID().toString():input.path("patientId").asText(),
                "subject",input.path("subject").asText(),"requestHash",input.path("requestHash").asText(),"status","ACTIVE")));
            ex.getResponseHeaders().add("Content-Type","application/json");ex.sendResponseHeaders(200,body.length);ex.getResponseBody().write(body);ex.close();
        }); server.start();
        try {
            var adapter=new HttpPatientProvisioningAdapter("http://127.0.0.1:"+server.getAddress().getPort()+"/provision","onboarding","synthetic-secret");
            assertThatCode(()->adapter.ensureProfile(r)).doesNotThrowAnyException();
            wrong.set(true);assertThatThrownBy(()->adapter.ensureProfile(r)).isInstanceOf(PatientOnboardingException.class);
        } finally {server.stop(0);}
    }
    @Test void refusesUnprotectedOrNonLocalHttpConfiguration() {
        var r=new Reservation(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"{}","{}","a".repeat(64));
        assertThatThrownBy(()->new HttpPatientProvisioningAdapter("http://example.test/provision","onboarding","synthetic-secret").ensureProfile(r))
            .isInstanceOf(PatientOnboardingException.class);
        assertThatThrownBy(()->new HttpPatientProvisioningAdapter("http://127.0.0.1:1/provision","onboarding","").ensureProfile(r))
            .isInstanceOf(PatientOnboardingException.class);
    }
}
