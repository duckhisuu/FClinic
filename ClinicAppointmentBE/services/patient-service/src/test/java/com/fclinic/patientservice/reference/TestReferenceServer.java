package com.fclinic.patientservice.reference;

import com.fclinic.patientservice.identity.TestAuthServer;
import com.fclinic.patientservice.infrastructure.adapter.reference.*;
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
import java.util.function.LongFunction;
import static com.fclinic.patientservice.reference.ReferenceFixtures.*;

/** Test-only protected HTTPS reference provider DOUBLE. No remote service implementation claim. */
public final class TestReferenceServer implements AutoCloseable {
    public record Reply(int status,String contentType,String version,byte[] body,String location) { }
    public record Request(String method,String rawPath,long id,boolean originalBearerForwarded) { }
    public final ReferenceHttpClient.Profile profile;
    public final String clientId, secret=UUID.randomUUID().toString();
    public final List<Request> requests=new CopyOnWriteArrayList<>();
    public final AtomicInteger calls=new AtomicInteger(), mutations=new AtomicInteger();
    public volatile boolean rejectAuthentication;
    public volatile LongFunction<Reply> handler;
    private final TestAuthServer.TestTls tls;
    private final HttpsServer server;
    private final ExecutorService pool=Executors.newVirtualThreadPerTaskExecutor();
    public TestReferenceServer(ReferenceHttpClient.Profile profile) { this(profile,TestAuthServer.TLS); }
    public TestReferenceServer(ReferenceHttpClient.Profile profile,TestAuthServer.TestTls tls) {
        this.profile=profile; this.tls=tls;
        clientId=profile==ReferenceHttpClient.Profile.DOCTOR?"patient-doctor-ref-test":"patient-appointment-ref-test";
        handler=id -> json(200,profile==ReferenceHttpClient.Profile.DOCTOR?doctor(id,true):appointment(id,PATIENT,12,"CONFIRMED"));
        try {
            var keys=KeyStore.getInstance("PKCS12");
            try(var in=Files.newInputStream(tls.store)) { keys.load(in,tls.password.toCharArray()); }
            var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); km.init(keys,tls.password.toCharArray());
            var ssl=SSLContext.getInstance("TLS"); ssl.init(km.getKeyManagers(),null,null);
            server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.setHttpsConfigurator(new HttpsConfigurator(ssl)); server.setExecutor(pool);
            server.createContext(collectionPath(),this::handle); server.start();
        } catch(Exception e) { pool.shutdownNow(); throw new IllegalStateException("Reference test server failed",e); }
    }
    public URI collectionUri() { return URI.create("https://localhost:"+server.getAddress().getPort()+collectionPath()); }
    private String collectionPath() { return profile==ReferenceHttpClient.Profile.DOCTOR?"/api/v1/doctors":"/internal/v1/appointments"; }
    public ReferenceSettings.Endpoint settings(Duration budget) {
        return new ReferenceSettings.Endpoint(true,collectionUri(),clientId,secret,Duration.ofMillis(500),budget,
                4096,4,tls.store,tls.password);
    }
    public Reply json(int status,Map<String,Object> body) { return new Reply(status,"application/json",profile.version,bytes(body),null); }
    private void handle(HttpExchange exchange) throws java.io.IOException {
        calls.incrementAndGet();
        try(exchange) {
            if(!exchange.getRequestMethod().equals("GET")) { mutations.incrementAndGet(); exchange.sendResponseHeaders(405,-1); return; }
            String expected="Basic "+Base64.getEncoder().encodeToString((clientId+":"+secret).getBytes(StandardCharsets.UTF_8));
            if(rejectAuthentication || !expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401,-1); return;
            }
            if(!profile.version.equals(exchange.getRequestHeaders().getFirst(ReferenceHttpClient.VERSION_HEADER))
                    || !"application/json".equals(exchange.getRequestHeaders().getFirst("Accept"))
                    || exchange.getRequestURI().getRawQuery()!=null || exchange.getRequestBody().readAllBytes().length!=0) {
                exchange.sendResponseHeaders(400,-1); return;
            }
            String raw=exchange.getRequestURI().getRawPath();
            String suffix=raw.substring(collectionPath().length());
            if(!suffix.matches("/[1-9][0-9]*"+profile.suffix)) { exchange.sendResponseHeaders(404,-1); return; }
            String idPart=suffix.substring(1,suffix.length()-profile.suffix.length());
            long id=Long.parseLong(idPart);
            requests.add(new Request(exchange.getRequestMethod(),raw,id,
                    exchange.getRequestHeaders().getFirst("Authorization").startsWith("Bearer ")));
            Reply reply=handler.apply(id);
            exchange.getResponseHeaders().set("Content-Type",reply.contentType());
            if(reply.version()!=null) exchange.getResponseHeaders().set(ReferenceHttpClient.VERSION_HEADER,reply.version());
            if(reply.location()!=null) exchange.getResponseHeaders().set("Location",reply.location());
            exchange.sendResponseHeaders(reply.status(),reply.body().length); exchange.getResponseBody().write(reply.body());
        } catch(RuntimeException e) { /* Test fault closes connection; never synthesize reference success. */ }
    }
    public void close() { server.stop(0); pool.shutdownNow(); }
}

