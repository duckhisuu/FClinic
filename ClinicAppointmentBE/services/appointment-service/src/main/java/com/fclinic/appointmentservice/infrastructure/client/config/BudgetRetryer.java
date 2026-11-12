package com.fclinic.appointmentservice.infrastructure.client.config;

import com.fclinic.appointmentservice.domain.AppointmentError;
import feign.*;

public final class BudgetRetryer implements Retryer {
  private int attempts = 1;
  public void continueOrPropagate(RetryableException failure) {
    if (!(failure instanceof BudgetFailure budget) || attempts++ >= 2
        || budget.deadline - System.nanoTime() <= 100_000_000L) throw AppointmentError.unavailable();
    try { Thread.sleep(100); }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw AppointmentError.unavailable(); }
  }
  public Retryer clone() { return new BudgetRetryer(); }
  static final class BudgetFailure extends RetryableException {
    final long deadline;
    BudgetFailure(Request request, long deadline) {
      super(503, "Reference provider unavailable", request.httpMethod(), (Long) null, request);
      this.deadline = deadline;
    }
  }
}

