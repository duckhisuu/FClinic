package com.fclinic.patientservice.identity;

import com.fclinic.patientservice.infrastructure.adapter.identity.IdentitySettings;
import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** TLS Auth CONTRACT DOUBLE. Generated test certificate, never packaged into main resources. */
public final class TestAuthServer implements AutoCloseable {
    public record Reply(int status, String contentType, byte[] body) {
        public static Reply json(int status, byte[] body) { return new Reply(status,"application/json",body); }
    }
    public static final class TestTls {
        public final Path store;
        public final String password = UUID.randomUUID().toString();
        public TestTls(String subjectAlternativeNames) {
            try {
                Path root=Files.createTempDirectory(Path.of("target").toAbsolutePath(),"identity-test-tls-");
                store=root.resolve("test-only.p12");
                String keytool=Path.of(System.getProperty("java.home"),"bin",
                        System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString();
                var process=new ProcessBuilder(keytool,"-genkeypair","-alias","auth-test","-keyalg","RSA",
                        "-keysize","2048","-validity","2","-storetype","PKCS12","-keystore",store.toString(),
                        "-storepass",password,"-keypass",password,"-dname","CN=localhost",
                        "-ext","SAN="+subjectAlternativeNames).redirectErrorStream(true).start();
                if (!process.waitFor(20,TimeUnit.SECONDS)) { process.destroyForcibly(); throw new AssertionError("keytool timeout"); }
                if(process.exitValue()!=0) throw new AssertionError("test keytool failed");
            } catch(Exception e) { throw new ExceptionInInitializerError(e); }
        }
        SSLContext serverContext() throws Exception {
            var keys=KeyStore.getInstance("PKCS12");
            try(var in=Files.newInputStream(store)) { keys.load(in,password.toCharArray()); }
            var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            km.init(keys,password.toCharArray());
            var context=SSLContext.getInstance("TLS"); context.init(km.getKeyManagers(),null,null); return context;
        }
    }
    public static final TestTls TLS = new TestTls("dns:localhost,ip:127.0.0.1");
    private final TestTls tls;
    private final HttpsServer server;
    private final ExecutorService pool=Executors.newVirtualThreadPerTaskExecutor();
    public final String clientSecret=UUID.randomUUID().toString();
    public final AtomicInteger calls=new AtomicInteger(), authenticatedCalls=new AtomicInteger();
    public volatile Function<String,Reply> handler=token -> Reply.json(200,IdentityFixtures.bytes(IdentityFixtures.user()));
    public TestAuthServer() {
        this(TLS);
    }
    public TestAuthServer(TestTls tls) {
        this.tls=tls;
        try {
            server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.setHttpsConfigurator(new HttpsConfigurator(tls.serverContext()));
            server.setExecutor(pool);
            server.createContext("/introspection",this::handle); server.start();
        } catch(Exception e) { pool.shutdownNow(); throw new IllegalStateException("Test Auth server failed",e); }
    }
    public URI endpoint() { return URI.create("https://localhost:"+server.getAddress().getPort()+"/introspection"); }
    public IdentitySettings settings(Duration budget) {
        return new IdentitySettings(true,endpoint(),"https://identity.example.test","patient-service",
                IdentityFixtures.CLIENT_ID,clientSecret,Duration.ofMillis(500),budget,65536,4,
                tls.store,tls.password,IdentityFixtures.settings().services());
    }
    private void handle(HttpExchange exchange) throws java.io.IOException {
        calls.incrementAndGet();
        try(exchange) {
            String expected="Basic "+Base64.getEncoder().encodeToString((IdentityFixtures.CLIENT_ID+":"+clientSecret)
                    .getBytes(StandardCharsets.UTF_8));
            if(!"POST".equals(exchange.getRequestMethod()) || !expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))
                    || !"application/x-www-form-urlencoded".equals(exchange.getRequestHeaders().getFirst("Content-Type"))) {
                exchange.sendResponseHeaders(401,-1); return;
            }
            authenticatedCalls.incrementAndGet();
            String form=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            Map<String,String> fields=new HashMap<>();
            for(String field:form.split("&")) {
                String[] pair=field.split("=",2);
                fields.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
            }
            if(!"access_token".equals(fields.get("token_type_hint")) || fields.size()!=2) {
                exchange.sendResponseHeaders(400,-1); return;
            }
            Reply reply=handler.apply(fields.get("token"));
            exchange.getResponseHeaders().set("Content-Type",reply.contentType());
            exchange.sendResponseHeaders(reply.status(),reply.body().length);
            exchange.getResponseBody().write(reply.body());
        } catch(RuntimeException e) { /* Test handler failure is a closed connection, never an identity grant. */ }
    }
    @Override public void close() { server.stop(0); pool.shutdownNow(); }
}

