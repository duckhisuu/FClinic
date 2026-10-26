package com.fclinic.patientservice.contract;

import com.fclinic.patientservice.infrastructure.adapter.reference.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;

/** Test-only read-only probe for upstream statuses hidden by production503 translation. Never serves a provider. */
final class ProviderProbe implements AutoCloseable {
    record Response(int status,HttpHeaders headers,byte[] bytes) { }
    private final HttpClient client;
    private final ReferenceSettings.Endpoint settings;
    ProviderProbe(ReferenceSettings.Endpoint settings) throws Exception {
        this.settings=settings; settings.validate();
        var tls=new SSLParameters(); tls.setProtocols(new String[]{"TLSv1.3","TLSv1.2"}); tls.setEndpointIdentificationAlgorithm("HTTPS");
        var builder=HttpClient.newBuilder().connectTimeout(settings.connectTimeout()).sslParameters(tls)
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1);
        if(settings.trustStore()!=null) {
            var store=KeyStore.getInstance("PKCS12"); char[] password=settings.trustStorePassword().toCharArray();
            try(var in=Files.newInputStream(settings.trustStore())) { store.load(in,password); }
            finally { Arrays.fill(password,'\0'); }
            var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(store);
            var context=SSLContext.getInstance("TLS");context.init(null,tm.getTrustManagers(),null);builder.sslContext(context);
        }
        client=builder.build();
    }
    Response get(long id,ReferenceHttpClient.Profile profile,boolean wrongVersion) throws Exception {
        String credentials=URLEncoder.encode(settings.clientId(),StandardCharsets.UTF_8)+":"+URLEncoder.encode(settings.clientSecret(),StandardCharsets.UTF_8);
        var uri=URI.create(settings.collectionUri().toASCIIString()+"/"+id+profile.suffix);
        var request=HttpRequest.newBuilder(uri).GET().timeout(settings.exchangeTimeout())
                .header("Authorization","Basic "+Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .header(ReferenceHttpClient.VERSION_HEADER,wrongVersion?"unsupported-patient-contract/0":profile.version)
                .header("Accept","application/json").header("Cache-Control","no-store").build();
        var future=client.sendAsync(request,info -> new BodyLimit());
        try {
            var result=future.get(settings.exchangeTimeout().toMillis(),TimeUnit.MILLISECONDS);
            return new Response(result.statusCode(),result.headers(),result.body());
        } finally { if(!future.isDone()) future.cancel(true); }
    }
    public void close() { client.shutdownNow(); }
    private static final class BodyLimit implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> done=new CompletableFuture<>();
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream(); Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return done; }
        public void onSubscribe(Flow.Subscription s) {subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> items) {
            for(var item:items) {
                if(item.remaining()>65536-bytes.size()) {subscription.cancel();done.completeExceptionally(new IOException("Response limit"));return;}
                byte[] next=new byte[item.remaining()];item.get(next);bytes.writeBytes(next);
            } subscription.request(1);
        }
        public void onError(Throwable e) {done.completeExceptionally(e);}
        public void onComplete() {done.complete(bytes.toByteArray());}
    }
}

