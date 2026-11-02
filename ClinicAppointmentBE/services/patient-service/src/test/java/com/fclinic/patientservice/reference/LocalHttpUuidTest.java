package com.fclinic.patientservice.reference;

import com.fclinic.patientservice.infrastructure.adapter.reference.*;
import com.fclinic.patientservice.domain.model.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.fclinic.patientservice.UuidFixtures.*;
import static com.fclinic.patientservice.reference.ReferenceFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** Real TCP loopback; provider DOUBLE, not an actual Doctor/Appointment integration. */
class LocalHttpUuidTest {
    static ReferenceSettings.Endpoint settings(String uri,String id,String secret) {
        return new ReferenceSettings.Endpoint(true,URI.create(uri),id,secret,Duration.ofMillis(300),
                Duration.ofSeconds(2),4096,2,null,null);
    }
    @ParameterizedTest @ValueSource(strings={"http://localhost:8082/api/v1/doctors","http://127.0.0.1/api/v1/doctors",
            "http://[::1]:8082/api/v1/doctors","https://localhost/api/v1/doctors","https://provider.example.test/api/v1/doctors"})
    void explicitLoopbackHttpOrVerifiedHttpsConfigurationAccepted(String uri) {
        settings(uri,"synthetic-client","synthetic-secret").validate();
    }
    @ParameterizedTest @ValueSource(strings={"http://external.example.test/doctors","http://192.168.1.2/doctors",
            "http://0.0.0.0/doctors","http://8.8.8.8/doctors","http://doctor-service/doctors",
            "http://127.0.0.2/doctors","http://localhost.evil.test/doctors","http://user:password@localhost/doctors",
            "http://localhost/doctors?secret=x","http://localhost/doctors#fragment","http://localhost/doctors/",
            "http://localhost/a/../doctors","http://localhost/%2e%2e/doctors","http://localhost//doctors",
            "http://localhost:0/doctors","http://localhost:70000/doctors","http://localhost","not-a-uri"})
    void unsafeTargetsRejectedBeforeNetwork(String uri) {
        assertThatThrownBy(() -> settings(uri,"synthetic-client","synthetic-secret").validate()).isInstanceOf(RuntimeException.class);
    }
    @Test void malformedUriAndMissingCredentialsCannotInitializeTransport() {
        assertThatThrownBy(() -> settings("http://[broken/path","client","secret")).isInstanceOf(IllegalArgumentException.class);
        for(String secret:new String[]{null,""," "})
            assertThatThrownBy(() -> new RemoteDoctorClientAdapter(settings("http://localhost/doctors","client",secret)))
                    .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RemoteDoctorClientAdapter(settings("http://localhost/doctors",null,"secret")))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void canonicalUuidOnlyAndCountersStillNumeric() {
        assertThat(BusinessId.parse(fixture(12).toString())).isEqualTo(fixture(12));
        for(Object value:List.of(12,"12",fixture(12).toString().toUpperCase(),"550e8400-e29b-11d4-a716-44665544000c",
                "550e8400-e29b-41d4-7716-44665544000c","550e8400e29b41d4a71644665544000c"))
            assertThatThrownBy(() -> BusinessId.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void realHttpTcpBothUuidContractsAuthVersionAndStrictFailures() throws Exception {
        for(var profile:ReferenceHttpClient.Profile.values()) {
            var hits=new AtomicInteger(); var authenticated=new AtomicInteger(); var redirected=new AtomicInteger();
            var code=new AtomicInteger(200); var fault=new AtomicInteger();
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/redirect-target",exchange -> {redirected.incrementAndGet();exchange.close();});
            String path=profile==ReferenceHttpClient.Profile.DOCTOR?"/api/v1/doctors":"/internal/v1/appointments";
            server.createContext(path,exchange -> {
                hits.incrementAndGet();
                try(exchange) {
                    String expected="Basic "+Base64.getEncoder().encodeToString("demo-client:demo-secret".getBytes(StandardCharsets.UTF_8));
                    if(!expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                        exchange.sendResponseHeaders(401,-1);return;
                    }
                    authenticated.incrementAndGet();
                    assertThat(exchange.getRequestURI().getRawPath()).isEqualTo(path+"/"+fixture(12)+profile.suffix);
                    assertThat(exchange.getRequestMethod()).isEqualTo("GET");
                    assertThat(exchange.getRequestHeaders().getFirst(ReferenceHttpClient.VERSION_HEADER)).isEqualTo(profile.version);
                    assertThat(exchange.getRequestHeaders().getFirst("Accept")).isEqualTo("application/json");
                    assertThat(exchange.getRequestHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
                    var body=profile==ReferenceHttpClient.Profile.DOCTOR?doctor(fixture(12),true):appointment(fixture(12),PATIENT,fixture(13),"CONFIRMED");
                    if(code.get()==404) body=missing(profile==ReferenceHttpClient.Profile.DOCTOR?"DOCTOR_NOT_FOUND":"APPOINTMENT_NOT_FOUND");
                    if(fault.get()==3) data(body).put("id",12);
                    if(fault.get()!=1) exchange.getResponseHeaders().set(ReferenceHttpClient.VERSION_HEADER,profile.version);
                    exchange.getResponseHeaders().set("Content-Type","application/json");
                    exchange.getResponseHeaders().set("Location","http://127.0.0.1:"+server.getAddress().getPort()+"/redirect-target");
                    byte[] payload=fault.get()==2?"{".getBytes(StandardCharsets.UTF_8):bytes(body);
                    exchange.sendResponseHeaders(code.get(),payload.length);exchange.getResponseBody().write(payload);
                }
            });
            server.start();
            var endpoint=settings("http://localhost:"+server.getAddress().getPort()+path,"demo-client","demo-secret");
            try(var doctor=new RemoteDoctorClientAdapter(endpoint);var appointment=new RemoteAppointmentClientAdapter(endpoint)) {
                Runnable find=() -> { if(profile==ReferenceHttpClient.Profile.DOCTOR) doctor.find(fixture(12));else appointment.find(fixture(12)); };
                if(profile==ReferenceHttpClient.Profile.DOCTOR) assertThat(doctor.find(fixture(12)).orElseThrow().id()).isEqualTo(fixture(12));
                else assertThat(appointment.find(fixture(12)).orElseThrow().doctorId()).isEqualTo(fixture(13));
                code.set(404);
                if(profile==ReferenceHttpClient.Profile.DOCTOR) assertThat(doctor.find(fixture(12))).isEmpty();
                else assertThat(appointment.find(fixture(12))).isEmpty();
                code.set(200);
                for(int f:new int[]{1,2,3}) {fault.set(f);unavailable(find);}
                fault.set(0);
                for(int status:new int[]{302,401,403,500,503}) {code.set(status);int before=hits.get();unavailable(find);assertThat(hits.get()).isEqualTo(before+1);}
                assertThat(redirected).hasValue(0);
                assertThat(authenticated.get()).isEqualTo(hits.get());
                code.set(200);
                var wrong=settings(endpoint.collectionUri().toString(),"demo-client","wrong-disposable-secret");
                try(var denied=new RemoteDoctorClientAdapter(wrong)) {unavailable(() -> denied.find(fixture(12)));}
                assertThat(authenticated.get()).isEqualTo(hits.get()-1);
                server.stop(0);unavailable(find);
            } finally {server.stop(0);}
        }
    }
    static void unavailable(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PatientError.class,e -> {
            assertThat(e.code()).isEqualTo(PatientError.Code.DEPENDENCY_UNAVAILABLE);
            assertThat(e.getMessage()).isEqualTo("DEPENDENCY_UNAVAILABLE");assertThat(e.getCause()).isNull();
        });
    }
}
