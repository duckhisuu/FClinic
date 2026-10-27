package com.fclinic.appointmentservice.infrastructure;

import static com.fclinic.appointmentservice.infrastructure.AppointmentJson.*;

import com.fclinic.appointmentservice.domain.AppointmentError;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class ProviderHttp {
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(1))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public static long deadline() {
    return System.nanoTime() + Duration.ofSeconds(5).toNanos();
  }

  public JsonNode get(String url, String token, long deadline) {
    return exchange(url, token, null, deadline);
  }

  public JsonNode post(String url, String token, Object body, long deadline) {
    return exchange(url, token, body, deadline);
  }

  private JsonNode exchange(String url, String token, Object body, long deadline) {
    CompletableFuture<HttpResponse<byte[]>> pending = null;
    try {
      if (url == null || url.isBlank() || token == null || token.isBlank())
        throw AppointmentError.unavailable();
      URI uri = URI.create(url);
      String host = uri.getHost();
      require(
          host != null
              && uri.getUserInfo() == null
              && uri.getFragment() == null
              && Set.of("http", "https").contains(uri.getScheme()));
      long left = deadline - System.nanoTime();
      if (left <= 0) throw AppointmentError.unavailable();
      Duration timeout = Duration.ofNanos(Math.min(left, Duration.ofSeconds(2).toNanos()));
      var builder =
          HttpRequest.newBuilder(uri)
              .timeout(timeout)
              .header("Authorization", "Bearer " + token)
              .header("Accept", "application/json");
      if (body == null) builder.GET();
      else
        builder
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(write(body)));
      pending = client.sendAsync(builder.build(), info -> new LimitedBody());
      var response = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
      if (response.statusCode() == 404) throw new MissingReference();
      require(
          response.statusCode() == 200
              && response
                  .headers()
                  .firstValue("Content-Type")
                  .orElse("")
                  .toLowerCase(Locale.ROOT)
                  .startsWith("application/json"));
      JsonNode root = JSON.readTree(response.body());
      require(
          root != null
              && root.isObject()
              && root.path("status").isIntegralNumber()
              && root.path("status").intValue() == 200
              && root.path("message").isString()
              && root.has("data")
              && !root.path("data").isNull());
      return root.path("data");
    } catch (AppointmentError e) {
      throw e;
    } catch (MissingReference e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw AppointmentError.unavailable();
    } catch (Exception e) {
      throw AppointmentError.unavailable();
    } finally {
      if (pending != null && !pending.isDone()) pending.cancel(true);
    }
  }

  public static class MissingReference extends RuntimeException {}

  private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    final CompletableFuture<byte[]> result = new CompletableFuture<>();
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    Flow.Subscription subscription;

    public CompletionStage<byte[]> getBody() {
      return result;
    }

    public void onSubscribe(Flow.Subscription s) {
      subscription = s;
      s.request(1);
    }

    public void onNext(List<ByteBuffer> chunks) {
      for (var b : chunks) {
        if (b.remaining() > 262144 - bytes.size()) {
          subscription.cancel();
          result.completeExceptionally(new IOException("Response too large"));
          return;
        }
        byte[] next = new byte[b.remaining()];
        b.get(next);
        bytes.writeBytes(next);
      }
      subscription.request(1);
    }

    public void onError(Throwable e) {
      result.completeExceptionally(e);
    }

    public void onComplete() {
      result.complete(bytes.toByteArray());
    }
  }
}
