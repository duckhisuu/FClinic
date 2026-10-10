package com.fclinic.notificationservice.infrastructure.persistence;

import com.fclinic.notificationservice.domain.model.NotificationStatus;
import com.fclinic.notificationservice.infrastructure.entity.JpaNotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface SpringDataJpaNotificationRepository extends JpaRepository<JpaNotificationEntity, Long> {

    Page<JpaNotificationEntity> findByRecipientUserId(Long recipientUserId, Pageable pageable);

    Page<JpaNotificationEntity> findByStatus(NotificationStatus status, Pageable pageable);

    @Query("""
            select n.id from JpaNotificationEntity n
             where n.status in :statuses and n.nextAttemptAt <= :now
             order by n.createdAt asc
            """)
    List<Long> findDueIds(@Param("statuses") Collection<NotificationStatus> statuses,
                          @Param("now") Instant now, Pageable limit);

    @Query("""
            select n from JpaNotificationEntity n
             where n.status = :status and n.lastAttemptAt < :staleBefore
             order by n.lastAttemptAt asc
            """)
    List<JpaNotificationEntity> findStuck(@Param("status") NotificationStatus status,
                                          @Param("staleBefore") Instant staleBefore, Pageable limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update JpaNotificationEntity n
               set n.status = :processing,
                   n.lastAttemptAt = :now,
                   n.updatedAt = :now,
                   n.version = n.version + 1
             where n.id = :id and n.status in :claimable
            """)
    int claim(@Param("id") Long id, @Param("claimable") Collection<NotificationStatus> claimable,
              @Param("processing") NotificationStatus processing, @Param("now") Instant now);
}
