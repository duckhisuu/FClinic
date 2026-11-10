package com.fclinic.authservice.api.exception;

import com.fclinic.authservice.domain.exception.AuthException;
import com.fclinic.common.web.ApiResponse;
import com.fclinic.common.web.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(org.springframework.dao.DataIntegrityViolationException ex) {
        return ResponseEntity.status(409).body(ApiResponse.failure(409, ErrorCode.CONFLICT,
                "Tài khoản hoặc hồ sơ đã được liên kết. Vui lòng tải lại dữ liệu."));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(AuthException ex) {
        if (ex.getHttpStatus() >= 500) {
            log.error("[AuthException] status={} message={}", ex.getHttpStatus(), ex.getMessage(), ex);
        } else {
            log.warn("[AuthException] status={} message={}", ex.getHttpStatus(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.failure(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(HttpStatus.BAD_REQUEST.value(), ErrorCode.VALIDATION_ERROR, message));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception ex) {
        log.warn("[BadRequest] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(HttpStatus.BAD_REQUEST.value(), ErrorCode.VALIDATION_ERROR,
                        "Tham số hoặc định dạng UUID/JSON không hợp lệ"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure(HttpStatus.FORBIDDEN.value(), ErrorCode.FORBIDDEN,
                        "Bạn không có quyền truy cập tài nguyên này"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(HttpStatus.UNAUTHORIZED.value(), ErrorCode.UNAUTHORIZED,
                        "Xác thực thất bại hoặc phiên đăng nhập đã hết hạn"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.failure(HttpStatus.METHOD_NOT_ALLOWED.value(), ErrorCode.BAD_REQUEST,
                        "Phương thức HTTP không được hỗ trợ"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("[InternalError] Unexpected exception in auth-service", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCode.INTERNAL_SERVER_ERROR,
                        "Hệ thống gặp sự cố nội bộ. Vui lòng thử lại sau"));
    }
}

