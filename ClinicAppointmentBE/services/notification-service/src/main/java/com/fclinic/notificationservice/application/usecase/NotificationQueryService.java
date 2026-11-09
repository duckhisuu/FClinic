package com.fclinic.notificationservice.application.usecase;

import com.fclinic.notificationservice.application.port.in.QueryNotificationsUseCase;
import com.fclinic.notificationservice.application.port.out.NotificationRepositoryPort;
import com.fclinic.notificationservice.application.result.NotificationView;
import com.fclinic.notificationservice.domain.model.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.Objects;
import com.fclinic.notificationservice.application.exception.NotificationNotFoundException;

@Service
public class NotificationQueryService implements QueryNotificationsUseCase {

    private final NotificationRepositoryPort repository;
    private final NotificationStateService stateService;

    public NotificationQueryService(NotificationRepositoryPort repository, NotificationStateService stateService) {
        this.repository = repository;
        this.stateService = stateService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationView> getForUser(UUID userId, Pageable pageable) {
        Objects.requireNonNull(userId);
        return repository.findByRecipientUserId(userId, pageable).map(NotificationView::from);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationView getForUserById(UUID userId, UUID id) {
        Objects.requireNonNull(userId);
        return repository.findByIdAndRecipientUserId(id, userId).map(NotificationView::from)
                .orElseThrow(() -> new NotificationNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationView> getAll(NotificationStatus status, Pageable pageable) {
        Page<com.fclinic.notificationservice.domain.aggregate.Notification> page =
                status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable);
        return page.map(NotificationView::from);
    }

    @Override
    public NotificationView getById(UUID id) {
        return NotificationView.from(stateService.getRequired(id));
    }

    @Override
    public NotificationView retry(UUID id) {
        return NotificationView.from(stateService.manualRetry(id));
    }
}
