package com.fclinic.appointmentservice.infrastructure.client.config;

import static com.fclinic.appointmentservice.infrastructure.client.config.FeignHeaderGuards.*;
import com.fclinic.appointmentservice.domain.AppointmentError;
import feign.*;
import java.io.*;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.*;

/** Bounds the entire exchange, including a slowly streamed body, before decoding or error handling. */
public final class GuardedFeignClient implements Client, AutoCloseable {
  private final Client delegate;
  private final String contract;
  private final boolean retryGet;
  private final ExecutorService workers = new ThreadPoolExecutor(0, 32, 30, TimeUnit.SECONDS,
      new SynchronousQueue<>(), Thread.ofVirtual().name("appointment-feign-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
  public GuardedFeignClient(Client delegate, String contract, boolean retryGet) {
    this.delegate = delegate; this.contract = contract; this.retryGet = retryGet;
  }
  public Response execute(Request request, Request.Options options) {
    endpoint(URI.create(request.url()), request.httpMethod() == Request.HttpMethod.GET);
    boolean retryableGet = retryGet && request.httpMethod() == Request.HttpMethod.GET;
    long deadline = options instanceof FeignCalls.DeadlineOptions budget ? budget.deadline() : FeignCalls.deadline();
    long left = Math.min(deadline - System.nanoTime(), Duration.ofSeconds(2).toNanos());
    require(left > 0);
    int millis = (int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(left));
    Future<Response> pending = null;
    try {
      pending = workers.submit(() -> {
        try (Response response = delegate.execute(request, new Request.Options(Math.min(1000, millis), millis, false))) {
          contract(response.headers(), contract);
          byte[] body;
          if (response.body() == null) body = new byte[0];
          else {
            try (InputStream in = response.body().asInputStream()) { body = in.readNBytes(262145); }
            require(body.length <= 262144);
          }
          return response.toBuilder().body(body).build();
        }
      });
      Response response = pending.get(left, TimeUnit.NANOSECONDS);
      if (retryableGet && response.status() == 503) {
        response.close(); throw new BudgetRetryer.BudgetFailure(request, deadline);
      }
      return response;
    } catch (BudgetRetryer.BudgetFailure e) { throw e; }
    catch (ExecutionException e) {
      if (retryableGet && e.getCause() instanceof IOException) throw new BudgetRetryer.BudgetFailure(request, deadline);
      throw AppointmentError.unavailable();
    } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw AppointmentError.unavailable(); }
    catch (TimeoutException e) {
      if (retryableGet && deadline - System.nanoTime() > 0) throw new BudgetRetryer.BudgetFailure(request, deadline);
      throw AppointmentError.unavailable();
    } catch (RejectedExecutionException e) { throw AppointmentError.unavailable(); }
    finally { if (pending != null && !pending.isDone()) pending.cancel(true); }
  }
  public void close() { workers.shutdownNow(); }
}

