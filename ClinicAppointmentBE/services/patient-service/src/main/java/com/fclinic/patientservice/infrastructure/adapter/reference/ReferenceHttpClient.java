package com.fclinic.patientservice.infrastructure.adapter.reference;

import com.fclinic.patientservice.domain.model.PatientError;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Authenticated read-only GET transport. No redirects, application retries, reference cache or bearer forwarding. */
public final class ReferenceHttpClient implements AutoCloseable {
    public static final String VERSION_HEADER="X-FClinic-Reference-Contract";
    public enum Profile {
        DOCTOR("patient-doctor-reference/1",""),
        APPOINTMENT("patient-appointment-medical-reference/1","/medical-reference");
        public final String version, suffix;
        Profile(String version,String suffix) { this.version=version; this.suffix=suffix; }
    }
    public record Response(int status,byte[] body) { }
    private final ReferenceSettings.Endpoint settings;
    private final Profile profile;
    private final HttpClient client;
    private final Semaphore capacity;
    private final String credential;
    public ReferenceHttpClient(ReferenceSettings.Endpoint settings,Profile profile) {
        settings.validate(); this.settings=settings; this.profile=profile; capacity=new Semaphore(settings.maxConcurrentRequests());
        credential="Basic "+Base64.getEncoder().encodeToString((encode(settings.clientId())+":"+encode(settings.clientSecret()))
                .getBytes(StandardCharsets.UTF_8));
        try {
            var tls=new SSLParameters(); tls.setProtocols(new String[]{"TLSv1.3","TLSv1.2"}); tls.setEndpointIdentificationAlgorithm("HTTPS");
            var builder=HttpClient.newBuilder().sslParameters(tls).connectTimeout(settings.connectTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1);
            if(settings.trustStore()!=null) builder.sslContext(trustContext(settings));
            client=builder.build();
        } catch(Exception e) { throw new IllegalStateException("Unable to initialize patient reference TLS client"); }
    }
    public Response get(long id) {
        ReferenceJson.requireId(id);
        if(TransactionSynchronizationManager.isActualTransactionActive() || !capacity.tryAcquire()) throw ReferenceJson.unavailable();
        CompletableFuture<HttpResponse<byte[]>> future=null;
        try {
            URI uri=URI.create(settings.collectionUri().toASCIIString()+"/"+Long.toString(id)+profile.suffix);
            var request=HttpRequest.newBuilder(uri).timeout(settings.exchangeTimeout())
                    .header("Authorization",credential).header("Accept","application/json")
                    .header(VERSION_HEADER,profile.version).header("Cache-Control","no-store").GET().build();
            future=client.sendAsync(request,info -> new LimitedBody(settings.maxResponseBytes()));
            var response=future.get(settings.exchangeTimeout().toMillis(),TimeUnit.MILLISECONDS);
            if((response.statusCode()!=200 && response.statusCode()!=404)
                    || response.headers().allValues(VERSION_HEADER).size()!=1
                    || !profile.version.equals(response.headers().firstValue(VERSION_HEADER).orElse(null))
                    || response.headers().allValues("Content-Type").size()!=1
                    || !response.headers().firstValue("Content-Type").orElse("").matches("(?i)application/json(?:\\s*;.*)?")
                    || response.headers().allValues("Content-Encoding").size()>1
                    || response.headers().firstValue("Content-Encoding").filter(v -> !v.equalsIgnoreCase("identity")).isPresent())
                throw ReferenceJson.unavailable();
            return new Response(response.statusCode(),response.body());
        } catch(PatientError e) { throw e; }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw ReferenceJson.unavailable(); }
        catch(Exception e) { throw ReferenceJson.unavailable(); }
        finally { if(future!=null && !future.isDone()) future.cancel(true); capacity.release(); }
    }
    private static SSLContext trustContext(ReferenceSettings.Endpoint settings) throws Exception {
        var store=KeyStore.getInstance("PKCS12"); char[] password=settings.trustStorePassword().toCharArray();
        try(var in=Files.newInputStream(settings.trustStore())) { store.load(in,password); }
        finally { Arrays.fill(password,'\0'); }
        var trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); trust.init(store);
        var context=SSLContext.getInstance("TLS"); context.init(null,trust.getTrustManagers(),null); return context;
    }
    private static String encode(String s) { return URLEncoder.encode(s,StandardCharsets.UTF_8); }
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
                    subscription.cancel(); result.completeExceptionally(new IOException("Reference response limit")); return;
                }
                byte[] next=new byte[item.remaining()]; item.get(next); bytes.writeBytes(next);
            }
            subscription.request(1);
        }
        public void onError(Throwable e) { result.completeExceptionally(e); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}

