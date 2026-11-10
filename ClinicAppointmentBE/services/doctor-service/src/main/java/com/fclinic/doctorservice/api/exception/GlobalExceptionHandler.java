package com.fclinic.doctorservice.api.exception;

import com.fclinic.common.web.ApiResponse;
import com.fclinic.common.web.error.ErrorCode;
import com.fclinic.doctorservice.application.exception.DoctorNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(org.springframework.dao.DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(409, ErrorCode.CONFLICT,
                "Khung giờ đã tồn tại hoặc dữ liệu đang xung đột. Vui lòng tải lại lịch."));
    }

    @ExceptionHandler(DoctorNotFoundException.class)
    public ResponseEntity<ReferenceError> handleNotFound(DoctorNotFoundException ex) {
        log.warn("[NotFound] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ReferenceError(404, ex.getMessage(), null, "DOCTOR_NOT_FOUND", java.util.List.of()));
    }

    public record ReferenceError(int status, String message, Object data, String code, java.util.List<?> fieldErrors) { }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[Validation] {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(HttpStatus.BAD_REQUEST.value(), ErrorCode.VALIDATION_ERROR, errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("[BadRequest] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(HttpStatus.BAD_REQUEST.value(), ErrorCode.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("[InternalError] {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        ErrorCode.INTERNAL_SERVER_ERROR, ex.getMessage()));
    }
}
