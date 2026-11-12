package com.fclinic.appointmentservice.security;

import static com.fclinic.appointmentservice.security.SecurityFixtures.*;
import static org.assertj.core.api.Assertions.*;
import com.fclinic.appointmentservice.domain.AppointmentError;
import com.fclinic.appointmentservice.infrastructure.client.config.*;
import com.fclinic.appointmentservice.infrastructure.security.AuthAppointmentScopeAdapter;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import tools.jackson.databind.node.ObjectNode;

class AuthScopeBoundaryTest {
  HttpServer server; FeignHarness harness; AuthAppointmentScopeAdapter adapter;
  Consumer<ObjectNode> change = n -> {};
  @BeforeEach void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    var security = settings(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/scope"));
    server.createContext("/scope",ex -> {
      assertThat(ex.getRequestHeaders().getFirst("Authorization"))
          .isEqualTo(FeignHeaderGuards.basic(security.scopeClientId(),security.scopeClientSecret()));
      var query = FeignJacksonSupport.JSON.readTree(ex.getRequestBody().readAllBytes());
      var proof = new LinkedHashMap<String,Object>();
      proof.put("assignmentId",ID);proof.put("active",true);proof.put("validFrom",CLOCK.instant().minusSeconds(60).toString());
      proof.put("validUntil",CLOCK.instant().plusSeconds(60).toString());proof.put("visitId",null);
      var response = new LinkedHashMap<String,Object>();
      response.put("contractVersion","appointment-work-scope/1");response.put("request",query);
      response.put("evaluatedAt",CLOCK.instant().toString());response.put("allowed",true);
      response.put("patientIds",query.path("operation").asText().equals("APPOINTMENT_SEARCH")?List.of(PATIENT):null);
      response.put("evidence",proof);
      ObjectNode node = (ObjectNode)FeignJacksonSupport.JSON.valueToTree(response);change.accept(node);
      byte[] bytes = FeignJacksonSupport.JSON.writeValueAsBytes(node);
      ex.getResponseHeaders().set("Content-Type","application/json");ex.sendResponseHeaders(200,bytes.length);
      try(var out=ex.getResponseBody()){out.write(bytes);}
    });
    server.start();harness=new FeignHarness();adapter=new AuthAppointmentScopeAdapter(harness.scope(security),security,CLOCK);
  }
  @AfterEach void close(){harness.close();server.stop(0);}
  boolean allowed(){return adapter.allowed(actor("ROLE_STAFF",null,null),"APPOINTMENT_CANCEL",PATIENT);}
  @Test void usesExactScopeEchoAndCurrentAssignment() {
    assertThat(allowed()).isTrue();assertThat(adapter.patients(actor("ROLE_STAFF",null,null))).containsExactly(PATIENT);
  }
  @Test void rejectsChangedEchoStaleEvaluationAndMalformedEvidence() {
    List<Consumer<ObjectNode>> mutations = List.of(
        n -> ((ObjectNode)n.path("request")).put("operation","APPOINTMENT_SEARCH"),
        n -> ((ObjectNode)n.path("request").path("caller")).remove("serviceName"),
        n -> n.put("evaluatedAt",CLOCK.instant().minusSeconds(2).toString()),
        n -> n.put("evaluatedAt",CLOCK.instant().plusSeconds(2).toString()),
        n -> n.put("contractVersion","wrong/1"),
        n -> ((ObjectNode)n.path("evidence")).put("assignmentId","1"),
        n -> ((ObjectNode)n.path("evidence")).put("validUntil",CLOCK.instant().minusSeconds(60).toString()),
        n -> ((ObjectNode)n.path("evidence")).put("visitId",ID.toString()),
        n -> ((ObjectNode)n.path("evidence")).put("active","true"),
        n -> ((ObjectNode)n.path("evidence")).put("unexpected",true),
        n -> n.put("allowed",false));
    for(var mutation:mutations) {
      change=mutation;
      assertThatThrownBy(this::allowed).isInstanceOfSatisfying(AppointmentError.class,e -> assertThat(e.status()).isEqualTo(503));
    }
  }
  @Test void inactiveFutureAndExpiredAssignmentsDoNotGrantAccess() {
    for(Consumer<ObjectNode> mutation:List.<Consumer<ObjectNode>>of(
        n -> ((ObjectNode)n.path("evidence")).put("active",false),
        n -> ((ObjectNode)n.path("evidence")).put("validFrom",CLOCK.instant().plusSeconds(1).toString()),
        n -> ((ObjectNode)n.path("evidence")).put("validUntil",CLOCK.instant().toString()))) {
      change=mutation;assertThat(allowed()).isFalse();
    }
    change=n -> {n.put("allowed",false);n.putNull("patientIds");n.putNull("evidence");};
    assertThat(allowed()).isFalse();
  }
  @Test void duplicateSearchIdsFailClosed() {
    change=n -> n.putArray("patientIds").add(PATIENT.toString()).add(PATIENT.toString());
    assertThatThrownBy(() -> adapter.patients(actor("ROLE_STAFF",null,null))).isInstanceOf(AppointmentError.class);
  }
}
