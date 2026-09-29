package com.fclinic.appointmentservice.application.port.out;

import java.util.function.Supplier;

public interface DistributedLockPort {
    <T> T executeWithLock(String lockKey, long waitTimeSeconds, long leaseTimeSeconds, Supplier<T> task);
}
