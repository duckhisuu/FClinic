package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fclinic.appointmentservice.application.port.out.DistributedLockPort;
import com.fclinic.appointmentservice.domain.AppointmentError;
import java.util.function.Supplier;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "appointment.lock-provider", havingValue = "redis")
public class RedissonDistributedLockAdapter implements DistributedLockPort {
  private final RedissonClient client;

  public RedissonDistributedLockAdapter(RedissonClient client) {
    this.client = client;
  }

  public <T> T executeWithLock(String key, Supplier<T> task) {
    try {
      var lock = client.getLock("appointment:operation:" + key);
      if (!lock.tryLock())
        throw AppointmentError.conflict(
            "OPERATION_IN_PROGRESS", "Yêu cầu đang được xử lý. Hãy thử lại với cùng khóa.");
      try {
        return task.get();
      } finally {
        if (lock.isHeldByCurrentThread()) lock.unlock();
      }
    } catch (org.redisson.client.RedisException failure) {
      throw AppointmentError.unavailable();
    }
  }
}
