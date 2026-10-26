package com.fclinic.patientservice.infrastructure.adapter.identity;

import com.fclinic.patientservice.application.port.out.IdentityVerifierPort;
import com.fclinic.patientservice.application.security.BearerTokens;
import com.fclinic.patientservice.application.security.TrustedCaller;
import com.fclinic.patientservice.domain.model.PatientError;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static com.fclinic.patientservice.domain.model.PatientError.Code.DEPENDENCY_UNAVAILABLE;

/** One HTTPS introspection per request. No token decoding, caching, redirects or application retries. */
public final class IntrospectionIdentityVerifier implements IdentityVerifierPort, AutoCloseable {
    private final IdentitySettings settings;
    private final IntrospectionDecoder decoder;
    private final HttpClient client;
    private final Semaphore capacity;
    private final String basicCredential;

    public IntrospectionIdentityVerifier(IdentitySettings settings, Clock clock) {
        settings.validateEnabled();
        this.settings = settings;
        decoder = new IntrospectionDecoder(settings, clock);
        capacity = new Semaphore(settings.maxConcurrentRequests());
        basicCredential = "Basic " + Base64.getEncoder().encodeToString((encode(settings.clientId()) + ":"
                + encode(settings.clientSecret())).getBytes(StandardCharsets.UTF_8));
        try {
            var builder = HttpClient.newBuilder().connectTimeout(settings.connectTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1);
            var tls = new SSLParameters();
            tls.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            tls.setEndpointIdentificationAlgorithm("HTTPS");
            builder.sslParameters(tls);
            if (settings.trustStore() != null) builder.sslContext(trustContext(settings));
            client = builder.build();
        } catch (Exception error) {
            // No URLs, paths, secrets or provider exception text in startup errors.
            throw new IllegalStateException("Unable to initialize patient identity TLS client");
        }
    }

    @Override public TrustedCaller verify(String bearerToken) {
        BearerTokens.requireToken(bearerToken);
        if (!capacity.tryAcquire()) throw unavailable();
        CompletableFuture<HttpResponse<byte[]>> exchange = null;
        try {
            var request = HttpRequest.newBuilder(settings.endpoint()).timeout(settings.exchangeTimeout())
                    .header("Authorization", basicCredential).header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("token=" + encode(bearerToken)
                            + "&token_type_hint=access_token", StandardCharsets.UTF_8)).build();
            exchange = client.sendAsync(request, info -> new LimitedBody(settings.maxResponseBytes()));
            var response = exchange.get(settings.exchangeTimeout().toMillis(), TimeUnit.MILLISECONDS);
            // A provider 401/403 is failure of Patient's technical credential, NOT human authentication.
            if (response.statusCode() != 200
                    || response.headers().allValues("Content-Type").size() != 1
                    || !response.headers().firstValue("Content-Type").orElse("").matches("(?i)application/json(?:\\s*;.*)?")
                    || response.headers().firstValue("Content-Encoding").filter(v -> !v.equalsIgnoreCase("identity")).isPresent())
                throw unavailable();
            return decoder.decode(response.body());
        } catch (PatientError error) { throw error; }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (Exception error) { throw unavailable(); }
        finally {
            if (exchange != null && !exchange.isDone()) exchange.cancel(true);
            capacity.release();
        }
    }

    private static SSLContext trustContext(IdentitySettings settings) throws Exception {
        var store = KeyStore.getInstance("PKCS12");
        char[] password = settings.trustStorePassword().toCharArray();
        try (var in = Files.newInputStream(settings.trustStore())) { store.load(in, password); }
        finally { Arrays.fill(password, '\0'); }
        var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        var context = SSLContext.getInstance("TLS");
        context.init(null, trust.getTrustManagers(), null);
        return context;
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static PatientError unavailable() { return new PatientError(DEPENDENCY_UNAVAILABLE); }
    @Override public void close() { client.shutdownNow(); }

    /** Cancel a large body while streaming, before it can accumulate unbounded memory. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int maximum;
        private Flow.Subscription subscription;
        LimitedBody(int maximum) { this.maximum = maximum; }
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription s) { subscription = s; s.request(1); }
        public void onNext(List<ByteBuffer> items) {
            for (var item : items) {
                if (item.remaining() > maximum - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IOException("Identity response limit")); return;
                }
                byte[] next = new byte[item.remaining()]; item.get(next); bytes.writeBytes(next);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}

