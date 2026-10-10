package com.fclinic.notificationservice.application.usecase;

import com.fclinic.notificationservice.application.port.in.QueryNotificationsUseCase;
import com.fclinic.notificationservice.application.port.out.NotificationRepositoryPort;
import com.fclinic.notificationservice.application.result.NotificationView;
import com.fclinic.notificationservice.domain.model.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public Page<NotificationView> getForUser(Long userId, Pageable pageable) {
        return repository.findByRecipientUserId(userId, pageable).map(NotificationView::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationView> getAll(NotificationStatus status, Pageable pageable) {
        Page<com.fclinic.notificationservice.domain.aggregate.Notification> page =
                status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable);
        return page.map(NotificationView::from);
    }

    @Override
    public NotificationView getById(Long id) {
        return NotificationView.from(stateService.getRequired(id));
    }

    @Override
    public NotificationView retry(Long id) {
        return NotificationView.from(stateService.manualRetry(id));
    }
}
