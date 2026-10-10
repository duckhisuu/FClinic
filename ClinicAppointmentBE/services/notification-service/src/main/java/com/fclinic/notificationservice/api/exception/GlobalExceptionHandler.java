package com.fclinic.notificationservice.api.exception;

import com.fclinic.common.web.ApiResponse;
import com.fclinic.common.web.error.ErrorCode;
import com.fclinic.notificationservice.application.exception.NotificationNotFoundException;
import com.fclinic.notificationservice.application.exception.NotificationNotRetryableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Uses the project-wide ApiResponse envelope; never leaks stack traces or exception internals. */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotificationNotFoundException ex) {
        return build(ErrorCode.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NotificationNotRetryableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotRetryable(NotificationNotRetryableException ex) {
        return build(ErrorCode.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        return build(ErrorCode.BAD_REQUEST, "Tham số yêu cầu không hợp lệ");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("[InternalError] Unexpected error in notification-service", ex);
        return build(ErrorCode.INTERNAL_SERVER_ERROR, null);
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message) {
        return ResponseEntity.status(code.defaultStatus()).body(ApiResponse.failure(code, message));
    }
}
