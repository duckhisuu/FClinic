package com.fclinic.patientservice.scope;

import com.fclinic.patientservice.identity.TestAuthServer;
import com.fclinic.patientservice.infrastructure.adapter.scope.*;
import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import static com.fclinic.patientservice.scope.ScopeFixtures.*;

/** Test-only authenticated HTTPS authority DOUBLE; no grants or credentials in production sources. */
public final class TestScopeServer implements AutoCloseable {
    public static final String ADMIN_ID="patient-admin-test", CLINICAL_ID="patient-clinical-test";
    public final String adminSecret=UUID.randomUUID().toString(), clinicalSecret=UUID.randomUUID().toString();
    public final AtomicInteger adminCalls=new AtomicInteger(), clinicalCalls=new AtomicInteger();
    public final List<Map<String,Object>> requests=new CopyOnWriteArrayList<>();
    public volatile boolean rejectAuthentication;
    public volatile BiFunction<ScopeQuery.Authority,Map<String,Object>,TestAuthServer.Reply> handler=
            (kind,request) -> TestAuthServer.Reply.json(200,bytes(grant(request,Set.of(PATIENT))));
    private final HttpsServer server;
    private final TestAuthServer.TestTls tls;
    private final ExecutorService pool=Executors.newVirtualThreadPerTaskExecutor();
    public TestScopeServer() { this(TestAuthServer.TLS); }
    public TestScopeServer(TestAuthServer.TestTls tls) {
        this.tls=tls;
        try {
            var keys=KeyStore.getInstance("PKCS12");
            try(var in=Files.newInputStream(tls.store)) { keys.load(in,tls.password.toCharArray()); }
            var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); km.init(keys,tls.password.toCharArray());
            var ssl=SSLContext.getInstance("TLS"); ssl.init(km.getKeyManagers(),null,null);
            server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.setHttpsConfigurator(new HttpsConfigurator(ssl)); server.setExecutor(pool);
            server.createContext("/administrative",e -> handle(e,ScopeQuery.Authority.ADMINISTRATIVE));
            server.createContext("/clinical",e -> handle(e,ScopeQuery.Authority.CLINICAL));
            server.start();
        } catch(Exception e) { pool.shutdownNow(); throw new IllegalStateException("Test scope server failed",e); }
    }
    public URI endpoint(ScopeQuery.Authority kind) {
        return URI.create("https://localhost:"+server.getAddress().getPort()+(kind==ScopeQuery.Authority.ADMINISTRATIVE?"/administrative":"/clinical"));
    }
    public ScopeSettings settings(Duration budget) {
        return new ScopeSettings(true,authority(ScopeQuery.Authority.ADMINISTRATIVE,budget),authority(ScopeQuery.Authority.CLINICAL,budget));
    }
    public ScopeSettings.Authority authority(ScopeQuery.Authority kind,Duration budget) {
        boolean admin=kind==ScopeQuery.Authority.ADMINISTRATIVE;
        return new ScopeSettings.Authority(endpoint(kind),admin?ADMIN_ID:CLINICAL_ID,admin?adminSecret:clinicalSecret,
                Duration.ofMillis(500),budget,4096,4,10,tls.store,tls.password);
    }
    @SuppressWarnings("unchecked")
    private void handle(HttpExchange exchange,ScopeQuery.Authority kind) throws java.io.IOException {
        (kind==ScopeQuery.Authority.ADMINISTRATIVE?adminCalls:clinicalCalls).incrementAndGet();
        try(exchange) {
            boolean admin=kind==ScopeQuery.Authority.ADMINISTRATIVE;
            String expected="Basic "+Base64.getEncoder().encodeToString(((admin?ADMIN_ID:CLINICAL_ID)+":"
                    +(admin?adminSecret:clinicalSecret)).getBytes(StandardCharsets.UTF_8));
            if(rejectAuthentication || !"POST".equals(exchange.getRequestMethod()) || !expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))
                    || !"application/json".equals(exchange.getRequestHeaders().getFirst("Content-Type"))) {
                exchange.sendResponseHeaders(401,-1); return;
            }
            Map<String,Object> request=JSON.readValue(exchange.getRequestBody().readAllBytes(),Map.class);
            if(!kind.name().equals(request.get("authority"))) { exchange.sendResponseHeaders(403,-1); return; }
            requests.add(request);
            var reply=handler.apply(kind,request);
            exchange.getResponseHeaders().set("Content-Type",reply.contentType());
            exchange.sendResponseHeaders(reply.status(),reply.body().length); exchange.getResponseBody().write(reply.body());
        } catch(RuntimeException e) { /* Test fault becomes closed connection, never a grant. */ }
    }
    public void close() { server.stop(0); pool.shutdownNow(); }
}

