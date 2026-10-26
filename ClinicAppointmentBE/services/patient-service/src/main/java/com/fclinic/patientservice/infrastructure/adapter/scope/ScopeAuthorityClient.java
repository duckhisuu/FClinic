package com.fclinic.patientservice.infrastructure.adapter.scope;

import com.fclinic.patientservice.domain.model.PatientError;
import javax.net.ssl.*;
import java.io.*;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static com.fclinic.patientservice.domain.model.PatientError.Code.DEPENDENCY_UNAVAILABLE;

/** Isolated authority credential/client/capacity; no identity credential or original bearer is forwarded. */
public final class ScopeAuthorityClient implements AutoCloseable {
    private final ScopeSettings.Authority settings;
    private final HttpClient client;
    private final Semaphore capacity;
    private final String credential;
    public ScopeAuthorityClient(ScopeSettings.Authority settings) {
        settings.validate(); this.settings=settings; capacity=new Semaphore(settings.maxConcurrentRequests());
        credential="Basic "+Base64.getEncoder().encodeToString((encode(settings.clientId())+":"+encode(settings.clientSecret()))
                .getBytes(StandardCharsets.UTF_8));
        try {
            var tls=new SSLParameters(); tls.setProtocols(new String[]{"TLSv1.3","TLSv1.2"});
            tls.setEndpointIdentificationAlgorithm("HTTPS");
            var builder=HttpClient.newBuilder().sslParameters(tls).connectTimeout(settings.connectTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1);
            if(settings.trustStore()!=null) builder.sslContext(trustContext(settings));
            client=builder.build();
        } catch(Exception e) { throw new IllegalStateException("Unable to initialize patient scope TLS client"); }
    }
    public byte[] evaluate(ScopeQuery query) {
        if(!capacity.tryAcquire()) throw unavailable();
        CompletableFuture<HttpResponse<byte[]>> future=null;
        try {
            var request=HttpRequest.newBuilder(settings.endpoint()).timeout(settings.exchangeTimeout())
                    .header("Authorization",credential).header("Content-Type","application/json")
                    .header("Accept","application/json").header("Cache-Control","no-store")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(ScopeDecisionDecoder.JSON.writeValueAsBytes(query.wire()))).build();
            future=client.sendAsync(request,info -> new LimitedBody(settings.maxResponseBytes()));
            var response=future.get(settings.exchangeTimeout().toMillis(),TimeUnit.MILLISECONDS);
            if(response.statusCode()!=200 || response.headers().allValues("Content-Type").size()!=1
                    || !response.headers().firstValue("Content-Type").orElse("").matches("(?i)application/json(?:\\s*;.*)?")
                    || response.headers().firstValue("Content-Encoding").filter(v -> !v.equalsIgnoreCase("identity")).isPresent())
                throw unavailable();
            return response.body();
        } catch(PatientError e) { throw e; }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch(Exception e) { throw unavailable(); }
        finally { if(future!=null && !future.isDone()) future.cancel(true); capacity.release(); }
    }
    private static SSLContext trustContext(ScopeSettings.Authority settings) throws Exception {
        var store=KeyStore.getInstance("PKCS12"); char[] password=settings.trustStorePassword().toCharArray();
        try(var in=Files.newInputStream(settings.trustStore())) { store.load(in,password); }
        finally { Arrays.fill(password,'\0'); }
        var trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); trust.init(store);
        var context=SSLContext.getInstance("TLS"); context.init(null,trust.getTrustManagers(),null); return context;
    }
    private static String encode(String s) { return URLEncoder.encode(s,StandardCharsets.UTF_8); }
    private static PatientError unavailable() { return new PatientError(DEPENDENCY_UNAVAILABLE); }
    public void close() { client.shutdownNow(); }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private final int max;
        private Flow.Subscription subscription;
        LimitedBody(int max) { this.max=max; }
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription s) { subscription=s; s.request(1); }
        public void onNext(List<ByteBuffer> items) {
            for(var item:items) {
                if(item.remaining()>max-bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IOException("Scope response limit")); return;
                }
                byte[] next=new byte[item.remaining()]; item.get(next); bytes.writeBytes(next);
            }
            subscription.request(1);
        }
        public void onError(Throwable e) { result.completeExceptionally(e); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}

