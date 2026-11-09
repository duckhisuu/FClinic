package com.fclinic.notificationservice.application.port.out;

import com.fclinic.notificationservice.domain.aggregate.Notification;
import com.fclinic.notificationservice.domain.model.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepositoryPort {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    Page<Notification> findByRecipientUserId(UUID userId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID userId);

    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

    Page<Notification> findAll(Pageable pageable);

    /** Oldest-first, bounded by limit. */
    List<UUID> findDueIds(Collection<NotificationStatus> statuses, Instant now, int limit);

    List<Notification> findStuckProcessing(Instant lastAttemptBefore, int limit);

    /** Atomically moves a PENDING/RETRY_SCHEDULED row to PROCESSING. False if another worker won. */
    boolean claimForProcessing(UUID id, Instant now);
}
