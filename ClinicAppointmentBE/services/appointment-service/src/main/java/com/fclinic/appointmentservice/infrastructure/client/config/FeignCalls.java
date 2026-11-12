package com.fclinic.appointmentservice.infrastructure.client.config;

import com.fclinic.appointmentservice.domain.AppointmentError;
import feign.Request;
import java.time.Duration;
import java.util.function.Supplier;

public final class FeignCalls {
  private FeignCalls() {}
  public static long deadline() { return System.nanoTime() + Duration.ofSeconds(5).toNanos(); }
  public static DeadlineOptions options(long deadline) {
    if (deadline - System.nanoTime() <= 0) throw AppointmentError.unavailable();
    return new DeadlineOptions(deadline);
  }
  public static <T> T call(Supplier<T> action) {
    try { return action.get(); }
    catch (AppointmentError e) { throw e; }
    catch (RuntimeException e) { throw AppointmentError.unavailable(); }
  }
  public static final class DeadlineOptions extends Request.Options {
    private final long deadline;
    public DeadlineOptions(long deadline) { super(Duration.ofSeconds(1), Duration.ofSeconds(2), false); this.deadline = deadline; }
    public long deadline() { return deadline; }
  }
}

