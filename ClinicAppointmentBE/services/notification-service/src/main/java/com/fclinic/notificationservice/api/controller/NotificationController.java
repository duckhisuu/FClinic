package com.fclinic.notificationservice.api.controller;

import com.fclinic.common.web.ApiResponse;
import com.fclinic.notificationservice.api.dto.NotificationResponse;
import com.fclinic.notificationservice.api.dto.PageResponse;
import com.fclinic.notificationservice.application.port.in.QueryNotificationsUseCase;
import com.fclinic.notificationservice.domain.model.NotificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Query/admin API only; delivery never goes through REST.
 * TODO: the project has no auth yet. When it does: /me uses the caller's id, {id} is owner/admin,
 * admin endpoints need ADMIN/RECEPTIONIST and retry needs ADMIN.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final QueryNotificationsUseCase queryUseCase;

    @GetMapping("/notifications/me")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getMine(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queryUseCase.getForUser(userId, pageable(page, size));
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(result, NotificationResponse::from)));
    }

    @GetMapping("/notifications/{id}")
    public ResponseEntity<ApiResponse<NotificationResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(NotificationResponse.from(queryUseCase.getById(id))));
    }

    @GetMapping("/admin/notifications")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getAll(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queryUseCase.getAll(status, pageable(page, size));
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(result, NotificationResponse::from)));
    }

    @PostMapping("/admin/notifications/{id}/retry")
    public ResponseEntity<ApiResponse<NotificationResponse>> retry(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã lên lịch gửi lại thông báo",
                NotificationResponse.from(queryUseCase.retry(id))));
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
