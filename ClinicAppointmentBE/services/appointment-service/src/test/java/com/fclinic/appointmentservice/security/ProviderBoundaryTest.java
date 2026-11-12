package com.fclinic.appointmentservice.security;

import com.fclinic.appointmentservice.infrastructure.client.config.FeignJacksonSupport;
import com.fclinic.appointmentservice.infrastructure.client.config.FeignHeaderGuards;

import com.fclinic.appointmentservice.config.*;
import com.fclinic.appointmentservice.infrastructure.*;
import com.fclinic.appointmentservice.infrastructure.adapter.FeignAppointmentReferenceAdapter;
import com.fclinic.appointmentservice.infrastructure.security.*;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.sun.net.httpserver.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderBoundaryTest {
  HttpServer server; FeignHarness http; FeignAppointmentReferenceAdapter adapter;
  UUID patient = UUID.randomUUID(), doctor = UUID.randomUUID(), account = UUID.randomUUID(), slot = UUID.randomUUID();
  AtomicReference<String> contract = new AtomicReference<>("appointment-recipient-accounts/1");
  @BeforeEach void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); server.start(); http = new FeignHarness();
    String url = "http://127.0.0.1:"+server.getAddress().getPort();
    var settings = new AppointmentSettings(new MockEnvironment().withProperty("appointment.patient-url", url).withProperty("appointment.doctor-url", url)
        .withProperty("appointment.recipient-url", url+"/accounts"));
    var tokens = mock(PatientServiceTokenProvider.class); when(tokens.token(anyLong())).thenReturn("patient-only");
    var credentials = new ProviderCredentials("doctor", "doctor-secret", "recipient", "recipient-secret");
    adapter = new FeignAppointmentReferenceAdapter(http.patient(url), http.doctor(url, credentials), http.recipients(credentials), settings, tokens);
    server.createContext("/api/v3/patients/", ex -> {
      assertThat(ex.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer patient-only");
      Map<String,Object> p = new LinkedHashMap<>(); p.put("id",patient); p.put("patientCode","PAT-"+patient); p.put("fullName","Current Patient"); p.put("phoneNumber","+84900000001");
      p.put("email","current@example.test"); p.put("status","INACTIVE"); p.put("version",0);
      for(String field: List.of("dateOfBirth","gender","address","createdAt","updatedAt","createdBy","updatedBy"))p.put(field,null);
      respond(ex, p, null);
    });
    server.createContext("/accounts", ex -> {
      assertThat(ex.getRequestHeaders().getFirst("Authorization")).isEqualTo(FeignHeaderGuards.basic("recipient","recipient-secret"));
      var request = FeignJacksonSupport.JSON.readTree(ex.getRequestBody().readAllBytes());
      assertThat(request.path("patientId").asText()).isEqualTo(patient.toString());
      Map<String,Object> data = new LinkedHashMap<>(); data.put("patientId",patient);data.put("doctorId",doctor);data.put("patientUserId",null);
      data.put("doctor",Map.of("userId",account,"name","Doctor Account","email","doctor@example.test"));
      respond(ex,data,contract.get());
    });
    server.createContext("/internal/v1/doctors/", ex -> {
      assertThat(ex.getRequestHeaders().getFirst("Authorization")).isEqualTo(FeignHeaderGuards.basic("doctor","doctor-secret"));
      respond(ex,List.of(Map.of("id",slot,"doctorId",doctor,"slotDate","2026-11-20","startTime","09:00:00","endTime","10:00:00","published",false)),"appointment-doctor-reference/1");
    });
  }
  void respond(HttpExchange ex,Object data,String header) throws java.io.IOException {
    byte[] bytes=FeignJacksonSupport.JSON.writeValueAsBytes(Map.of("status",200,"message","Synthetic","data",data));
    ex.getResponseHeaders().set("Content-Type","application/json"); if(header!=null)ex.getResponseHeaders().set("X-FClinic-Reference-Contract",header);
    ex.sendResponseHeaders(200,bytes.length);try(var out=ex.getResponseBody()){out.write(bytes);}
  }
  @AfterEach void close(){http.close();server.stop(0);}
  @Test void recipientUsesCurrentPatientContactAndSeparateExactAccountMapping(){
    var recipients=adapter.recipients(patient,doctor);
    assertThat(recipients.getFirst().userId()).isNull(); assertThat(recipients.getFirst().email()).isEqualTo("current@example.test");
    assertThat(recipients.get(1).userId()).isEqualTo(account);
  }
  @Test void rejectsWrongAccountContractAndNonLoopbackPlaintext(){
    contract.set("wrong/1");assertThatThrownBy(()->adapter.recipients(patient,doctor)).isInstanceOf(AppointmentError.class);
    assertThatThrownBy(()->http.patient("http://patient-service:8081").getPatient(patient,"Bearer token", com.fclinic.appointmentservice.infrastructure.client.config.FeignCalls.options(Long.MAX_VALUE))).isInstanceOf(AppointmentError.class);
  }
  @Test void hiddenOffersRemainHiddenInConsumer(){
    assertThat(adapter.offered(doctor,LocalDate.of(2026,11,20),Long.MAX_VALUE).getFirst().published()).isFalse();
  }
  @Test void maps404ByClientAndDoctorMethodAndRequiresContractEvenOn404() {
    server.removeContext("/api/v3/patients/");
    server.createContext("/api/v3/patients/", ex -> { ex.sendResponseHeaders(404,-1); ex.close(); });
    assertCode(() -> adapter.patient(patient,Long.MAX_VALUE),404,"PATIENT_NOT_FOUND");
    server.removeContext("/internal/v1/doctors/");
    server.createContext("/internal/v1/doctors/", ex -> {
      ex.getResponseHeaders().set("X-FClinic-Reference-Contract","appointment-doctor-reference/1");
      ex.sendResponseHeaders(404,-1);ex.close();
    });
    assertCode(() -> adapter.doctor(doctor,Long.MAX_VALUE),404,"DOCTOR_NOT_FOUND");
    assertCode(() -> adapter.offered(doctor,LocalDate.of(2026,11,20),Long.MAX_VALUE),404,"DOCTOR_NOT_FOUND");
    assertCode(() -> adapter.slot(doctor,slot,Long.MAX_VALUE),409,"SLOT_NOT_BOOKABLE");
    server.removeContext("/internal/v1/doctors/");
    server.createContext("/internal/v1/doctors/", ex -> { ex.sendResponseHeaders(404,-1);ex.close(); });
    assertCode(() -> adapter.doctor(doctor,Long.MAX_VALUE),503,"DEPENDENCY_UNAVAILABLE");
  }
  @Test void retriesIdempotentGetOn503WithinBudget() {
    var count=new java.util.concurrent.atomic.AtomicInteger();
    server.removeContext("/internal/v1/doctors/");
    server.createContext("/internal/v1/doctors/", ex -> {
      count.incrementAndGet();ex.getResponseHeaders().set("X-FClinic-Reference-Contract","appointment-doctor-reference/1");
      ex.sendResponseHeaders(503,-1);ex.close();
    });
    assertCode(() -> adapter.doctor(doctor,Long.MAX_VALUE),503,"DEPENDENCY_UNAVAILABLE");
    assertThat(count.get()).isEqualTo(2);
  }
  @Test void rejectsDuplicateContractHeaderAndDuplicateSlotIds() {
    server.removeContext("/internal/v1/doctors/");
    server.createContext("/internal/v1/doctors/", ex -> {
      ex.getResponseHeaders().add("X-FClinic-Reference-Contract","appointment-doctor-reference/1");
      ex.getResponseHeaders().add("X-FClinic-Reference-Contract","appointment-doctor-reference/1");
      byte[] bytes="{\"status\":200,\"message\":\"OK\",\"data\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      ex.getResponseHeaders().set("Content-Type","application/json");ex.sendResponseHeaders(200,bytes.length);
      try(var out=ex.getResponseBody()){out.write(bytes);}
    });
    assertCode(() -> adapter.offered(doctor,LocalDate.of(2026,11,20),Long.MAX_VALUE),503,"DEPENDENCY_UNAVAILABLE");
    server.removeContext("/internal/v1/doctors/");
    server.createContext("/internal/v1/doctors/", ex -> {
      var value=Map.of("id",slot,"doctorId",doctor,"slotDate","2026-11-20","startTime","09:00:00","endTime","10:00:00","published",true);
      respond(ex,List.of(value,value),"appointment-doctor-reference/1");
    });
    assertCode(() -> adapter.offered(doctor,LocalDate.of(2026,11,20),Long.MAX_VALUE),503,"DEPENDENCY_UNAVAILABLE");
  }
  void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,int status,String code) {
    assertThatThrownBy(action).isInstanceOfSatisfying(AppointmentError.class,e -> {
      assertThat(e.status()).isEqualTo(status);assertThat(e.code()).isEqualTo(code);
    });
  }
}
