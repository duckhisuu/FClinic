package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fclinic.appointmentservice.application.port.out.DistributedLockPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedissonDistributedLockAdapter implements DistributedLockPort {

    private final RedissonClient redissonClient;

    @Override
    public <T> T executeWithLock(String lockKey, long waitTimeSeconds, long leaseTimeSeconds, Supplier<T> task) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLocked = false;
        try {
            log.info("[RedissonLock] Attempting to acquire lock: {}", lockKey);
            isLocked = lock.tryLock(waitTimeSeconds, leaseTimeSeconds, TimeUnit.SECONDS);

            if (!isLocked) {
                log.warn("[RedissonLock] Could not acquire lock: {}", lockKey);
                throw new IllegalStateException("Hệ thống đang xử lý yêu cầu đặt khám cho khung giờ này. Vui lòng thử lại sau giây lát.");
            }

            log.info("[RedissonLock] Lock acquired successfully: {}", lockKey);
            return task.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Tiến trình bị gián đoạn trong khi chờ lock: " + lockKey, e);
        } finally {
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("[RedissonLock] Lock released: {}", lockKey);
            }
        }
    }
}
