package com.fclinic.appointmentservice.infrastructure.security;

import com.fclinic.appointmentservice.domain.AppointmentError;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** Bounded, no-redirect exchanges; HTTP is allowed only on literal loopback for local execution. */
@Component
public class AuthHttpTransport implements AutoCloseable {
  public static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).findAndAddModules().build();
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
      .followRedirects(HttpClient.Redirect.NEVER).build();
  private final Semaphore capacity = new Semaphore(32);
  public static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
  public static String basic(String id, String secret) {
    if (id == null || id.isBlank() || secret == null || secret.isBlank()) throw AppointmentError.unavailable();
    return "Basic " + Base64.getEncoder().encodeToString((encode(id) + ":" + encode(secret)).getBytes(StandardCharsets.UTF_8));
  }
  public JsonNode post(URI uri, String id, String secret, String contentType, String body, long deadline) {
    if (uri == null || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
        || uri.getFragment() != null || !("https".equals(uri.getScheme())
        || ("http".equals(uri.getScheme()) && Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost()))))
      throw AppointmentError.unavailable();
    if (!capacity.tryAcquire()) throw AppointmentError.unavailable();
    CompletableFuture<HttpResponse<byte[]>> future = null;
    try {
      long remaining = Math.min(deadline - System.nanoTime(), Duration.ofSeconds(2).toNanos());
      if (remaining <= 0) throw AppointmentError.unavailable();
      var request = HttpRequest.newBuilder(uri).timeout(Duration.ofNanos(remaining))
          .header("Authorization", basic(id, secret)).header("Accept", "application/json")
          .header("Content-Type", contentType).header("Cache-Control", "no-store")
          .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
      future = client.sendAsync(request, info -> new LimitedBody());
      var response = future.get(remaining, TimeUnit.NANOSECONDS);
      if (response.statusCode() != 200 || response.headers().allValues("Content-Type").size() != 1
          || !response.headers().firstValue("Content-Type").orElse("").matches("(?i)application/json(?:\\s*;.*)?")
          || response.headers().firstValue("Content-Encoding").filter(v -> !v.equalsIgnoreCase("identity")).isPresent())
        throw AppointmentError.unavailable();
      var result = JSON.readTree(response.body());
      if (result == null || !result.isObject()) throw AppointmentError.unavailable();
      return result;
    } catch (AppointmentError e) { throw e; }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw AppointmentError.unavailable(); }
    catch (Exception e) { throw AppointmentError.unavailable(); }
    finally { if (future != null && !future.isDone()) future.cancel(true); capacity.release(); }
  }
  public void close() { client.shutdownNow(); }
  private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    final CompletableFuture<byte[]> result = new CompletableFuture<>();
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    Flow.Subscription subscription;
    public CompletionStage<byte[]> getBody() { return result; }
    public void onSubscribe(Flow.Subscription s) { subscription = s; s.request(1); }
    public void onNext(List<ByteBuffer> chunks) {
      for (var b : chunks) {
        if (b.remaining() > 262144 - bytes.size()) {
          subscription.cancel(); result.completeExceptionally(new IOException("Response limit")); return;
        }
        byte[] next = new byte[b.remaining()]; b.get(next); bytes.writeBytes(next);
      }
      subscription.request(1);
    }
    public void onError(Throwable e) { result.completeExceptionally(e); }
    public void onComplete() { result.complete(bytes.toByteArray()); }
  }
}
