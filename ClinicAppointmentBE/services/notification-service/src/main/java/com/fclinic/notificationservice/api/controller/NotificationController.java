package com.fclinic.notificationservice.api.controller;

import com.fclinic.common.web.ApiResponse;
import com.fclinic.notificationservice.api.dto.NotificationResponse;
import com.fclinic.notificationservice.application.command.ProcessNotificationCommand;
import com.fclinic.notificationservice.application.port.in.ListNotificationsUseCase;
import com.fclinic.notificationservice.application.port.in.ProcessNotificationUseCase;
import com.fclinic.notificationservice.application.result.NotificationView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final ListNotificationsUseCase listNotificationsUseCase;
    private final ProcessNotificationUseCase processNotificationUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getRecentNotifications(
            @RequestParam(required = false, defaultValue = "50") int limit
    ) {
        List<NotificationResponse> list = listNotificationsUseCase.getRecentNotifications(limit)
                .stream().map(NotificationResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<NotificationResponse>> sendManualNotification(@RequestBody ProcessNotificationCommand command) {
        NotificationView view = processNotificationUseCase.processNotification(command);
        return ResponseEntity.ok(ApiResponse.success("Gửi thông báo thành công", NotificationResponse.from(view)));
    }
}
