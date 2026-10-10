package com.fclinic.notificationservice.infrastructure.adapter;

import com.fclinic.notificationservice.application.port.out.NotificationRepositoryPort;
import com.fclinic.notificationservice.domain.aggregate.Notification;
import com.fclinic.notificationservice.domain.model.NotificationStatus;
import com.fclinic.notificationservice.infrastructure.entity.JpaNotificationEntity;
import com.fclinic.notificationservice.infrastructure.mapper.NotificationEntityMapper;
import com.fclinic.notificationservice.infrastructure.persistence.SpringDataJpaNotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
public class JpaNotificationRepositoryAdapter implements NotificationRepositoryPort {

    private static final List<NotificationStatus> CLAIMABLE =
            List.of(NotificationStatus.PENDING, NotificationStatus.RETRY_SCHEDULED);

    private final SpringDataJpaNotificationRepository repository;
    private final NotificationEntityMapper mapper;

    public JpaNotificationRepositoryAdapter(SpringDataJpaNotificationRepository repository,
                                            NotificationEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Notification save(Notification notification) {
        JpaNotificationEntity target = notification.getId() == null
                ? new JpaNotificationEntity()
                : repository.findById(notification.getId()).orElseGet(JpaNotificationEntity::new);
        // The loaded entity carries the current version; a concurrent writer makes the flush fail.
        return mapper.toDomain(repository.save(mapper.toEntity(notification, target)));
    }

    @Override
    public Optional<Notification> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Page<Notification> findByRecipientUserId(Long userId, Pageable pageable) {
        return repository.findByRecipientUserId(userId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Notification> findByStatus(NotificationStatus status, Pageable pageable) {
        return repository.findByStatus(status, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Notification> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public List<Long> findDueIds(Collection<NotificationStatus> statuses, Instant now, int limit) {
        return repository.findDueIds(statuses, now, PageRequest.of(0, limit));
    }

    @Override
    public List<Notification> findStuckProcessing(Instant lastAttemptBefore, int limit) {
        return repository.findStuck(NotificationStatus.PROCESSING, lastAttemptBefore, PageRequest.of(0, limit))
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean claimForProcessing(Long id, Instant now) {
        return repository.claim(id, CLAIMABLE, NotificationStatus.PROCESSING, now) == 1;
    }
}
